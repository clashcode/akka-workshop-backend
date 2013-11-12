import akka.actor.{Actor, Props}
import com.clashcode.web.controllers.Application
import java.util.concurrent.TimeUnit
import play.api.Application
import play.api.libs.concurrent.Akka
import play.api.{Play, GlobalSettings, Logger, Application}
import scala.concurrent.duration.FiniteDuration
import play.api.libs.concurrent.Execution.Implicits._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    super.onStart(app)
    val actor = Akka.system(app).actorOf(Props[SimpleActor], "main")
    Logger.info(actor.toString)
  }

  override def onStop(app: Application) {
    super.onStop(app)
  }

}

class SimpleActor extends Actor {

  def receive = {
    case Hello(numbers) =>
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

case class Hello(numbers: Seq[Int])

