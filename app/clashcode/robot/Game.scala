package clashcode.robot

import scala.util.Random
import scala.collection.mutable

case class FieldPos(x: Int, y: Int)
case class GameState(points: Int, fpos: FieldPos)

/** represents a field with items which the robot has to collect */
case class Field(fieldSize: Int, itemCount: Int, items: Seq[Boolean])

/** represents a single game in which the robot has to collect all items on the given field */
class Game(field: Field, random: Random) {

  val items = mutable.ArraySeq(field.items : _*)
  var itemCount = field.itemCount
  var x = 0;
  var y = 0;

  /** get value of cell on position */
  private def cell(x: Int, y: Int) : Cell.Value = {
    if (y < 0 || x < 0 || x >= field.fieldSize || y >= field.fieldSize)
      Cell.WALL
    else if (items(y * field.fieldSize + x))
      Cell.STUFF
    else
      Cell.EMPTY
  }

  /** returns the current situation of the robot, represented by a unique index */
  def situationIndex : Int = {
    Situations.getIndex(Situations.getSituation(cell(x, y - 1), cell(x + 1, y), cell(x, y + 1), cell(x - 1, y), cell(x, y)))
  }

  /** pick up item if possible, return points */
  private def pickUp() : GameState = {
    val index = y * field.fieldSize + x
    if (items(index))
    {
      itemCount -= 1
      items(index) = false
      GameState(10, FieldPos(x, y)) // success: gain points
    }
    else
      GameState(-1, FieldPos(x, y)) // lost points
  }

  /** move robot if possible */
  private def move(dx: Int, dy: Int) : GameState = {
    val nextX = x + dx
    val nextY = y + dy
    if (cell(nextX, nextY) != Cell.WALL)
    {
      x = nextX
      y = nextY
      GameState(0, FieldPos(x, y)) // move successful
    }
    else
      GameState(-5, FieldPos(x, y)) // lost points
  }

  /** act as robot, returns points gained */
  def act(decision: Decision) : GameState = {
    decision match {
      case Move(dx, dy) => move(dx, dy)
      case MoveRandom =>
        val randomMove = Decisions.all(random.nextInt(4)).asInstanceOf[Move]
        move(randomMove.x, randomMove.y)
      case Stay => GameState(0, FieldPos(x, y))
      case PickUp => pickUp()
    }
  }

}

