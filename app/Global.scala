import actors.HostingActor
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import clashcode.{PrisonerResponse, PrisonerRequest, Hello, NameRequest}
import play.api.libs.concurrent.Akka
import play.api.{Play, GlobalSettings, Logger, Application}

object Global extends GlobalSettings {

  var maybeCluster = Option.empty[ActorSystem]

  override def onStart(app: Application) {
    super.onStart(app)

    // start hosting actor
    val actor = Akka.system(app).actorOf(Props[HostingActor], "main")
    Logger.info(actor.toString)

    // start second system (for clustering example)
    Play.configuration(app).getConfig("cluster").foreach(clusterConfig => {

      val system = ActorSystem("cluster", clusterConfig.underlying)
      maybeCluster = Some(system)
      val clusterListener = system.actorOf(Props[SimpleClusterListener], name = "clusterListener")
      Cluster(system).subscribe(clusterListener, classOf[ClusterDomainEvent])

      // test prisoner on main server
      system.actorOf(Props(classOf[Prisoner], "PrisonerX"), "player")
    })
  }

  override def onStop(app: Application) {
    super.onStop(app)
    maybeCluster.foreach(_.shutdown())
  }

}


class SimpleClusterListener extends Actor {
  def receive = {
    case state: CurrentClusterState ⇒
      Logger.info("Current members: " + state.members.mkString(", "))
    case MemberUp(member) ⇒
      Logger.info("Member is Up: " + member.address)
    case UnreachableMember(member) ⇒
      Logger.info("Member detected as unreachable: " + member)
    case MemberRemoved(member, previousStatus) ⇒
      Logger.info("Member is Removed: " + member.address + " after " + previousStatus)
    case _: ClusterDomainEvent ⇒ // ignore
  }
}




class Prisoner(name: String) extends Actor {

  def receive = {
    case _ : NameRequest =>
      //println("asked for name")
      sender ! Hello(name)
    case PrisonerRequest(other) => sender ! PrisonerResponse(true)
    case x => println(x)
  }

}

