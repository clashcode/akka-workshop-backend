import actors.HostingActor
import akka.actor.{Actor, Props}
import clashcode.Hello
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
    val actor = Akka.system(app).actorOf(Props[HostingActor], "main")
    Logger.info(actor.toString)
  }

  override def onStop(app: Application) {
    super.onStop(app)
  }

}





