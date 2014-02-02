import actors.HostingActor
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.routing.{ClusterRouterSettings, ClusterRouterConfig}
import akka.routing.BroadcastRouter
import com.clashcode.web.controllers.Application
import play.api.{Play, GlobalSettings, Application}
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

      // this router sends messages to up to 100 other "main" actors in the cluster
      val broadcastRouter = system.actorOf(Props.empty.withRouter(
        ClusterRouterConfig(
          BroadcastRouter(),
          ClusterRouterSettings(totalInstances = 100, routeesPath = "/user/main", allowLocalRoutees = true, useRole = None))),
        name = "router")

      // start tournament hoster
      val hostingActor = system.actorOf(Props(classOf[HostingActor], broadcastRouter), "main")
      Application.maybeHostingActor = Some(hostingActor)

      // hosting actor listens to cluster events
      Cluster(system).subscribe(hostingActor, classOf[ClusterDomainEvent])

    })
  }

  override def onStop(app: Application) {
    super.onStop(app)
    maybeCluster.foreach(_.shutdown())
  }

}

