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
    val actor = Akka.system(app).actorOf(Props[SimpleCalculatorActor], "simpleCalculator")
    Logger.info(actor.toString)
  }

  override def onStop(app: Application) {
    super.onStop(app)
  }

}

class SimpleCalculatorActor extends Actor {
  def receive = {
    case Add(n1, n2) ⇒ {
      println("Calculating %d + %d".format(n1, n2))
      val result = AddResult(n1, n2, n1 + n2)
      Application.maybeChannel.foreach(_.push(result.toString))
      sender ! result
      val replyTo = sender
      Akka.system(Play.current).scheduler.scheduleOnce(FiniteDuration(1, TimeUnit.SECONDS)) {
        Logger.info("replying late")
        replyTo ! AddResult(n1, n2, 111)
      }

    }
    case Subtract(n1, n2) ⇒
      println("Calculating %d - %d".format(n1, n2))
      sender ! SubtractResult(n1, n2, n1 - n2)
  }
}

trait MathOp

case class Add(nbr1: Int, nbr2: Int) extends MathOp

case class Subtract(nbr1: Int, nbr2: Int) extends MathOp

trait MathResult

case class AddResult(nbr: Int, nbr2: Int, result: Int) extends MathResult

case class SubtractResult(nbr1: Int, nbr2: Int, result: Int) extends MathResult
