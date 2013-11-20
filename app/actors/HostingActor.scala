package actors

import akka.actor.{ActorRef, Actor}
import clashcode.Hello
import com.clashcode.web.controllers.Application
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import org.joda.time.{Seconds, DateTime}
import scala.collection.mutable
import play.api.libs.concurrent.Execution.Implicits._


/**
 * Continuously sends game requests to all participants, keeps player high score.
 * Keeps list of upcoming tournament pairings
 */
class HostingActor extends Actor {

  /** timer for running tournament rounds */
  context.system.scheduler.scheduleOnce(FiniteDuration(1, TimeUnit.SECONDS)) {
    context.self ! TournamentTick()
  }

  /** list of players (max 100) */
  val players = mutable.Map.empty[String, Player]

  /** list of played games (max 10000) */
  val games = mutable.Queue.empty[Game]

  def receive = {

    case Hello(rawName) => // receive players name

      // add player to list
      val now = DateTime.now
      val name = rawName.take(12) // trim name to 12 chars max
      val player = players.getOrElseUpdate(name, new Player(name, sender, 0, 0, 0, now))
      player.lastSeen = now

      // log event
      val response = "Welcome, " + player.name + " from " + player.ref.path.address.host.getOrElse("???")
      logStatus(response)
      sender ! response

      // remove old players
      while (players.size > 100)
      {
        val lastPlayer = players.values.toSeq.sortBy(p => Seconds.secondsBetween(now, p.lastSeen).getSeconds).last
        players -= lastPlayer.name
      }
      /*
      val replyTo = sender
      Akka.system(Play.current).scheduler.scheduleOnce(FiniteDuration(1, TimeUnit.SECONDS)) {
        Logger.info("replying late")
        replyTo ! AddResult(n1, n2, 111)
      }
      */


    case _ : TournamentTick =>

    case text : String =>
      println("received " + text)
      Application.push(sender.path.address.host.getOrElse("anonymous") + ": " + text)
      sender ! ("you sent: " + text)
  }

  private def logStatus(status: String) {
    Application.push(status)
  }

}

case class TournamentTick()

class Player(val name: String,
             var ref: ActorRef,
             var points: Int,
             var games: Int,
             var ping: Int,
             var lastSeen: DateTime)

case class Turn(player: Player,
                start: DateTime,
                response: DateTime,
                cooperate: Option[Boolean], /** true, false, or none for timeout */
                points: Int)

case class Game(turn1: Turn, turn2: Turn)