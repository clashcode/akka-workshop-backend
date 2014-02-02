package actors

import akka.actor._
import com.clashcode.web.controllers.Application
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import scala.collection.mutable
import play.api.libs.concurrent.Execution.Implicits._
import akka.cluster.ClusterEvent._
import play.api.Logger
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.CurrentClusterState
import clashcode.robot.{RobotCode, Robot}
import scala.util.Random

/**
 * Listens to messages of all participants in cluster, keeps player high score.
 */
class HostingActor(broadcast: ActorRef, myIp: String) extends Actor {

  /** map of players (max 100) from ip address to player object */
  val players = mutable.Map.empty[String, Player]

  val actorPath = "/user/main" // default actor path
  var lastRobot = DateTime.now.minusSeconds(10) // last robot submission

  /** timer for running tournament rounds */
  case object Tick
  context.system.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), FiniteDuration(1, TimeUnit.SECONDS)) {
    self ! Tick
  }
  self ! Tick

  def receive = {

    case robot: Robot => handleRobot(robot, sender)

    case Tick => everySecond()

    case ResetStats => players.clear()

    case state: CurrentClusterState ⇒
      Logger.info("Current members: " + state.members.mkString(", "))

    case MemberUp(member) ⇒
      Logger.info("Member is Up: " + member.address)
      setStatus(getPlayerByAddress(member.address), "Online")

    case UnreachableMember(member) ⇒
      Logger.info("Member detected as unreachable: " + member)
      setStatus(getPlayerByAddress(member.address), "Unreachable")

    case MemberRemoved(member, previousStatus) ⇒
      Logger.info("Member is Removed: " + member.address + " after " + previousStatus)
      setStatus(getPlayerByAddress(member.address), "Offline")

    case _: ClusterDomainEvent ⇒ // ignore

    case x => // handle unknown messages
      val response = "Unknown message " + x.toString + " from " + sender.path.address.host.getOrElse("???")
      sender ! response
      logStatus(response)

  }

  /** set status of player */
  private def setStatus(player: Player, status: String) {
    player.lastSeen = DateTime.now
    player.status = status
  }

  /** get or create new player by actor address */
  private def getPlayerByAddress(address: Address) : Player = {
    val host = address.host.getOrElse(myIp)
    players.getOrElseUpdate(host, new Player(
      name = "anonymous-" + host,
      ref = context.actorFor(address + actorPath),
      robots = 0,
      best = None,
      lastSeen = DateTime.now,
      status = "Online"
    ))
  }

  /** handle new received robot */
  private def handleRobot(rawRobot: Robot, sender: ActorRef) {

    val player = getPlayerByAddress(sender.path.address)
    val robot = rawRobot.code.evaluate // prevent cheating by recalculating points
    lastRobot = DateTime.now

    // update player
    setStatus(player, "Online")
    player.name = robot.code.creatorName
    player.robots += 1
    player.ref = sender

    // determine best robot
    val best = player.best.getOrElse(robot)
    player.best = Some(if (best.points >= robot.points) best else robot)
    //Logger.info("received robot")

    //broadcast ! RobotCode.createRandomCode("Backend").evaluate
  }

  /** handle regular broadcasting of robots */
  private def everySecond() {

    // send a robot to the cluster
    val seconds = (DateTime.now.getMillis - lastRobot.getMillis) / 1000
    if (seconds >= 5) {
      val maybeRobot = Random.shuffle(players.values.map(_.best).flatten).headOption
      val randomRobot = maybeRobot.getOrElse({
        val random = RobotCode.createRandomCode("Backend")
        random.copy(code = random.code.map(_ => 5.toByte)).evaluate
      })
      broadcast ! randomRobot
      //Logger.info("broadcasting robot")
    }

    // update visualization
    Application.push(players.values.toList)
    //Logger.info(players.keys.mkString(", "))

  }

  var lastStatus = ""
  private def logStatus(status: String) {
    if (status == lastStatus) return
    lastStatus = status
    Application.push(status)
  }

}
