package actors

import akka.actor.{ActorRef, Actor}
import clashcode.{PrisonerResponse, PrisonerRequest, Hello}
import com.clashcode.web.controllers.Application
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import org.joda.time.{Seconds, DateTime}
import scala.collection.mutable
import play.api.libs.concurrent.Execution.Implicits._
import akka.pattern.{ ask, pipe }
import akka.util.Timeout


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

    case Hello(rawName) => // receive players name

      // add player to list
      val now = DateTime.now
      val name = rawName.take(12) // trim name to 12 chars max
      val isNew = !players.contains(name)
      val player = players.getOrElseUpdate(name, new Player(name, sender, 0, 0, 0, now, true))
      player.lastSeen = now
      player.active = true
      player.ref = sender // update actor reference

      // log event
      val response = (if (isNew) "Welcome, " else "Hi again, ") + player.name + " from " + player.ref.path.address.host.getOrElse("???")

      logStatus(response)
      sender ! response

      // remove old players
      while (players.size > 100)
      {
        val lastPlayer = players.values.toSeq.sortBy(p => Seconds.secondsBetween(now, p.lastSeen).getSeconds).last
        players -= lastPlayer.name
      }

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

    case x : String => // handle unknown messages
      val response = "Unknown message " + x.toString + " from " + sender.path.address.host.getOrElse("???")
      sender ! response
      logStatus(response)
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
      val turns = games.flatMap(_.turns.find(t => t.player == player && t.cooperate.isDefined))
      player.games = turns.size
      player.points = turns.map(_.points).sum
      player.ping = turns.map(t => (t.response.getMillis - t.start.getMillis).toInt).sum
    })

    // send updated high score
    logStatus("games: " + games.size)

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
             var ref: ActorRef,
             var points: Int,
             var games: Int,
             var ping: Int,
             var lastSeen: DateTime,
             var active: Boolean)

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