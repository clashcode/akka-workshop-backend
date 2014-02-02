package clashcode.robot

/** Cell represents all 3 possible cell types */
object Cell extends Enumeration {
  val EMPTY = Value(0)
  val WALL = Value(1)
  val STUFF = Value(2)
}

/** lists all 128 situations that the robot can ever encounter. */
object Situations {

  /** for performance we only deal with int representations.
    * in order to not confuse encoded situations with their indices, a type alias is provided */
  type Situation = Int

  /** generate all 128 possible situations for the robots 5 sensors
    * (excluding impossible situations like surrounded by walls, etc.) */
  val all : IndexedSeq[Situation] = {
    val result = for {
      center <- List(Cell.EMPTY, Cell.STUFF)
      topCell <- Cell.values
      rightCell <- Cell.values
      bottomCell <- Cell.values if (topCell != Cell.WALL || bottomCell != Cell.WALL)
      leftCell <- Cell.values if (rightCell != Cell.WALL || leftCell != Cell.WALL)
    } yield (getSituation(topCell, rightCell, bottomCell, leftCell, center))
    result.toIndexedSeq
  }

  val count = all.length

  private val indexBySituation = (0 to all.max).map(situation => all.indexOf(situation)).toArray

  /** maps situation to its index */
  def getIndex(situation: Situation) : Int = indexBySituation(situation)

  /** generate situation from given robot sensor states */
  def getSituation(top: Cell.Value, right: Cell.Value, bottom: Cell.Value, left: Cell.Value, center: Cell.Value) : Situation =
    (top.id * 3 * 3 * 3 * 3 + right.id * 3 * 3 * 3 + bottom.id * 3 * 3 + left.id * 3 + center.id)


}
