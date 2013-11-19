package actors

import akka.actor.Actor
import clashcode.Hello
import com.clashcode.web.controllers.Application

/**
 * Continuously sends game requests to all participants, keeps player high score.
 * Keeps list of upcoming tournament pairings
 */
class HostingActor extends Actor {



  def receive = {

    // receive prisoners name
    case Hello(name) =>
      println("Calculating " + numbers.toString)
      val result = numbers.sum
      Application.push(sender.path.address.host.getOrElse("anonymous") + ": " + result.toString)
      sender ! result
      /*
      val replyTo = sender
      Akka.system(Play.current).scheduler.scheduleOnce(FiniteDuration(1, TimeUnit.SECONDS)) {
        Logger.info("replying late")
        replyTo ! AddResult(n1, n2, 111)
      }
      */
    case text : String =>
      println("received " + text)
      Application.push(sender.path.address.host.getOrElse("anonymous") + ": " + text)
      sender ! ("you sent: " + text)
  }

}
