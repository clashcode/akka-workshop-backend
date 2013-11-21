package actors

import akka.actor._
import clashcode._
import com.clashcode.web.controllers.Application
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import org.joda.time.{Seconds, DateTime}
import scala.collection.mutable
import play.api.libs.concurrent.Execution.Implicits._
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import akka.cluster.ClusterEvent._
import play.api.Logger
import akka.cluster.ClusterEvent.MemberRemoved
import scala.Some
import clashcode.Hello
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.MemberUp
import akka.actor.Identify
import akka.cluster.ClusterEvent.CurrentClusterState
import clashcode.PrisonerResponse
import clashcode.PrisonerRequest


/**
 * Continuously sends game requests to all participants, keeps player high score.
 * Keeps list of upcoming tournament pairings
 */
class HostingActor extends Actor {

  /** list of players (max 100) */
  val players = mutable.Map.empty[String, Player]

  /** list of played games (max 5000) */
  val games = mutable.Queue.empty[Game]

  /** list of currently running games */
  val running = mutable.Queue.empty[Game]

  /** list of upcoming games */
  val upcoming = mutable.Queue.empty[Game]

  /** timer for running tournament rounds */
  context.system.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), FiniteDuration(1, TimeUnit.SECONDS)) {
    self ! TournamentTick()
  }

  def receive = {

    case Hello(rawName) => handleHello(sender, rawName, false)

    // handle ongoing tournaments
    case _ : TournamentTick =>

      // lets check the list of running games for timeouts
      val now = DateTime.now
      running.filter(g => !g.timedOut(now).isEmpty).foreach(timeoutGame => {
        running.dequeueFirst(_ == timeoutGame)

        // finalize game: other player gets default win point on time out
        val timeoutTurns = timeoutGame.timedOut(now)
        val maybeWinnerTurn = timeoutGame.turns.diff(timeoutTurns).headOption
        val defaultWinnerTurn = maybeWinnerTurn.map(_.copy(points = 1))
        val finalGame = Game(timeoutTurns ++ defaultWinnerTurn)
        addGame(finalGame)

        // handle player timeout
        timeoutTurns.foreach(t => {
          val response = "Hey " + t.player.name + ", we didn't get your response in time.";
          logStatus(response)
          t.player.ref ! response
          t.player.active = false // remove player from upcoming tournaments
        })
      })

      // no running or upcoming games? start new tournament if we have players
      val activePlayers = players.values.toSeq.filter(_.active)
      if (running.length == 0 && upcoming.length == 0) {

        if (activePlayers.length >= 2)
        {
          // round robin tournament
          val newGames = activePlayers.flatMap(player => {

            // create all games where this player is first player (ordered alphabetically)
            val opponents = activePlayers.filter(_.name > player.name)
            opponents.map(opponent => Game(List(
              Turn(player, now, now.plusSeconds(1), None, 0),
              Turn(opponent, now, now.plusSeconds(1), None, 0)
            )))

          })

          logStatus("Starting new tournament with " + activePlayers.length + " players, " + newGames.length + " games.")
          upcoming.enqueue(newGames : _*)
        }
        else if (activePlayers.length == 1) {
          logStatus("Only one player connected (hello, " + activePlayers.head.name + ")")
        }
        else {
          logStatus("No players connected")
        }

      }

      //logStatus("No players connected x")

      // start upcoming games (use clone of upcoming queue, since we're modifying it inside)
      List(upcoming : _*).foreach(upcomingGame => {

        val runningPlayers = running.flatMap(_.players)
        val availablePlayers = activePlayers.diff(runningPlayers)

        // if both players for this game available, start the game
        if (upcomingGame.players.forall(availablePlayers.contains)) {

          upcoming.dequeueFirst(_ == upcomingGame)

          val start = DateTime.now
          val runningGame = upcomingGame.copy(upcomingGame.turns.map(_.copy(
            start = start,
            response = start.plusSeconds(1)))) // set timeout to 1 second
          running.enqueue(runningGame)

          // get player responses
          implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS)) // needed for `?` below
          upcomingGame.players.foreach(player => {
            val otherPlayer = upcomingGame.players.filter(_ != player).headOption.getOrElse(player)
            (player.ref ? PrisonerRequest(otherPlayer.name)).foreach {
              case response : PrisonerResponse => self ! PlayerResponse(player, otherPlayer, response)
              case x =>
                val response = "Unknown message " + x.toString + " from " + player.name
                player.ref ! response
                logStatus(response)
            }
          })

        }

      })

      // still no running games? not enough active players. remove upcoming games, wait for new tournament.
      if (running.length == 0) {
        upcoming.clear()
      }

      // update high score list to web socket
      Application.push(players.values.toSeq.sortBy(- _.points))


    // handle response of a player
    case PlayerResponse(player, otherPlayer, response) =>

      val now = DateTime.now
      player.lastSeen = now

      val maybeGame = running.find(g => g.hasPlayers(Seq(player, otherPlayer)))

      if (!maybeGame.isDefined)
      {
        val response = "Sorry " + player.name + ", your response came too late.";
        logStatus(response)
        player.ref ! response
      }
      //else logStatus("got response from " + player.name)

      // update game
      maybeGame.foreach(game => {

        running.dequeueFirst(_ == game)

        // update points and turn info
        val playerTurn = game.turns.find(_.player == player).head
        val otherTurn = game.turns.find(_ != playerTurn).head
        val (playerPoints, otherPoints) = otherTurn.cooperate.fold((0, 0))(other =>
          (getPoints(response.cooperate, other), getPoints(other, response.cooperate)))

        val newPlayerTurn = playerTurn.copy(
          response = now,
          cooperate = Some(response.cooperate),
          points = playerPoints)

        val newOtherTurn = otherTurn.copy(points = otherPoints)

        // handle updated game
        val newGame = Game(List(newPlayerTurn, newOtherTurn))
        if (otherTurn.cooperate.isDefined)
          addGame(newGame) // finalize game
        else
          running.enqueue(newGame) // game still running, keep waiting for other response

      })

    case ResetStats =>

      // reset all tournament points
      games.clear()
      players.values.foreach(player => {
        player.coop = 1.0
        player.games = 0
        player.points = 0
      })

    case state: CurrentClusterState ⇒
      Logger.info("Current members: " + state.members.mkString(", "))

    case MemberUp(member) ⇒
      Logger.info("Member is Up: " + member.address)

      // try to discover player using cluster
      val playerRef = context.actorFor(member.address + "/user/player")
      implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS)) // needed for `?` below

      (playerRef ? NameRequest).mapTo[Hello].foreach(hello => handleHello(playerRef, hello.name, true))

    case UnreachableMember(member) ⇒
      Logger.info("Member detected as unreachable: " + member)

    case MemberRemoved(member, previousStatus) ⇒
      Logger.info("Member is Removed: " + member.address + " after " + previousStatus)

    case _: ClusterDomainEvent ⇒ // ignore

    case x => // handle unknown messages
      val response = "Unknown message " + x.toString + " from " + sender.path.address.host.getOrElse("???")
      sender ! response
      logStatus(response)

  }

  /** received players name */
  private def handleHello(sender: ActorRef, rawName: String, cluster: Boolean) {

    // add player to list
    val now = DateTime.now
    val name = rawName.take(12) // trim name to 12 chars max
    val isNew = !players.contains(name)
    val player = players.getOrElseUpdate(name, new Player(name, sender, 0, 0, 0, now, true, 1.0, cluster))
    player.lastSeen = now
    player.active = true
    player.ref = sender // update actor reference
    player.cluster = cluster // update whether player was discovered via cluster

    // log event
    val response = (if (isNew) "Welcome, " else "Hi again, ") +
      player.name + " from " + (if (cluster) "Cluster " else "") +
      player.ref.path.address.host.getOrElse("???")

    logStatus(response)
    sender ! response

    // remove old players
    while (players.size > 100)
    {
      val lastPlayer = players.values.toSeq.sortBy(p => Seconds.secondsBetween(now, p.lastSeen).getSeconds).last
      players -= lastPlayer.name
    }

  }

  /** get points for player cooperation / defect */
  private def getPoints(player: Boolean, other: Boolean) = {
    if (player && other) 1 // both cooperate
    else if (player) -1 // defect other player
    else if (other) 2 // other player cooperates, we defect
    else 0 // both defect
  }

  /** handle completed game, update statistics */
  private def addGame(game: Game) {

    // add game to archive, prune out old games
    games.enqueue(game)
    while(games.length > 5000) games.dequeue()

    // update player statistics
    game.players.foreach(player => {

      // notify players about result
      val myTurn = game.turns.find(_.player == player).getOrElse(game.turns.head)
      val otherTurn = game.turns.find(_ != myTurn).getOrElse(game.turns.last)

      otherTurn.cooperate.foreach(cooperate =>
        player.ref ! PrisonerResult(otherTurn.player.name, cooperate))

      // stats
      val turns = games.flatMap(_.turns.find(t => t.player == player && t.cooperate.isDefined))
      player.games = turns.size
      player.points = turns.map(_.points).sum
      player.ping = turns.map(t => (t.response.getMillis - t.start.getMillis).toInt).sum / turns.length.max(1)
      val cooperations = turns.map(t => if (t.cooperate.getOrElse(true)) 1 else 0).sum
      player.coop = cooperations / player.games.max(1).toDouble
    })

    // send updated game
    Application.push(game)
  }

  var lastStatus = ""
  private def logStatus(status: String) {
    if (status == lastStatus) return
    lastStatus = status
    Application.push(status)
  }

}

case class TournamentTick()

case class PlayerResponse(player: Player, otherPlayer: Player, response: PrisonerResponse)

class Player(val name: String,
             var ref: ActorRef, // actor endpoint for communication with this player
             var points: Int, // total score
             var games: Int, // number of games completed
             var ping: Int, // average response time in ms
             var lastSeen: DateTime, // last message from this player
             var active: Boolean, // does player answer to requests?
             var coop: Double, // how cooperative is this player?
             var cluster: Boolean) // is player discovered in cluster?

case class Turn(player: Player,
                start: DateTime,
                response: DateTime, /** 1 sec after start of turn, or actual response time of player */
                cooperate: Option[Boolean], /** true, false, or none for timeout */
                points: Int)

case class Game(turns: List[Turn]) {
  if (turns.length != 2) throw new IllegalArgumentException("turns")
  def timedOut(now: DateTime) : List[Turn] = turns.filter(t => t.cooperate.isEmpty && t.response.isBefore(now))
  def hasPlayers(seq: Seq[Player]) : Boolean = seq.forall(players.contains)
  lazy val players = turns.map(_.player)
}

case object ResetStats