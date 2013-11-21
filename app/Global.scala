import actors.HostingActor
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import clashcode.Hello
import clashcode.PrisonerRequest
import clashcode.PrisonerResponse
import clashcode.{PrisonerResponse, PrisonerRequest, Hello, NameRequest}
import com.clashcode.web.controllers.Application
import play.api.Application
import play.api.{Play, GlobalSettings, Logger, Application}
import scala.Some

object Global extends GlobalSettings {

  var maybeCluster = Option.empty[ActorSystem]

  override def onStart(app: Application) {
    super.onStart(app)

    // start second system (for clustering example)
    Play.configuration(app).getConfig("cluster").foreach(clusterConfig => {

      // create cluster system
      val system = ActorSystem("cluster", clusterConfig.underlying)
      maybeCluster = Some(system)

      // start tournament hoster
      val hostingActor = system.actorOf(Props[HostingActor], "main")
      Application.maybeHostingActor = Some(hostingActor)

      // hosting actor listens to cluster events
      Cluster(system).subscribe(hostingActor, classOf[ClusterDomainEvent])

      // start test prisoner on main server
      system.actorOf(Props(classOf[Prisoner], "PrisonerX"), "player")
    })
  }

  override def onStop(app: Application) {
    super.onStop(app)
    maybeCluster.foreach(_.shutdown())
  }

}



class Prisoner(name: String) extends Actor {

  def receive = {
    case NameRequest => sender ! Hello(name)
    case PrisonerRequest(other) => sender ! PrisonerResponse(true)
    case x : String => println(x)
  }

}

