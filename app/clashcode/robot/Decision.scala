package clashcode.robot

/** decision represents one of the 6 possible decisions of the robot (in each situation) */
trait Decision

case class Move(x: Int, y: Int) extends Decision
case object MoveRandom extends Decision
case object Stay extends Decision
case object PickUp extends Decision

object Decisions {

  /** list of all 6 possible decisions */
  val all : IndexedSeq[Decision] = IndexedSeq(
    Move(0, -1),
    Move(+1, 0),
    Move(0, +1),
    Move(-1, 0),
    PickUp,
    MoveRandom)

  val count = all.length

  /** mapping between decision objects and their integer representation (0 to 5) */
  val lookup : Map[Decision, Int] = all.zipWithIndex.toMap

}
