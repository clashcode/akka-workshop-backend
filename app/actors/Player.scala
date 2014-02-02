package actors

import akka.actor.ActorRef
import org.joda.time.DateTime
import clashcode.robot.Robot

class Player(var name: String,
             var ref: ActorRef, // actor endpoint for communication with this player
             var robots: Int, // number of submitted robots
             var best: Option[Robot], // best submitted robot
             var lastSeen: DateTime,// last message from this player
             var status: String
             )

case object ResetStats
