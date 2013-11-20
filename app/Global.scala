import actors.HostingActor
import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.{GlobalSettings, Logger, Application}

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





