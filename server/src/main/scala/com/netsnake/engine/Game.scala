package com.netsnake.engine

import akka.actor.{Actor, ActorLogging, Props}
import com.netsnake.engine.GameCommands.MoveUp

object Game {
  def props: Props = Props(new Game)
}

trait GameResponse
object GameOutput {
  case class GameState(playerId: String, snakes: List[Snake], apple: Apple)
}

final case class Point(x: Int, y: Int) {
  def +(other: Point) = Point(this.x + other.x, this.y + other.y)
  def -(other: Point) = Point(this.x - other.x, this.y - other.y)
}

object GameCommands {
  trait GameCommand
  case object Start extends GameCommand
  case object MoveUp extends GameCommand
  case object Invalid extends GameCommand
  case object AddPlayer extends GameCommand
}


trait Direction
case object Up extends Direction
case object Down extends Direction
case object Right extends Direction
case object Left extends Direction

case class Snake(head: Point, tail: List[Point], direction: Direction)
case class Apple(pos: Point)

class Game extends Actor with ActorLogging {
  import GameCommands._
  private val players = Map[String, Int]
  private val snakes = List[Snake]

  override def receive: Receive = {
    case Start =>
      start
      sender() ! "No!"
    case cmd => log.info(s"Received: $cmd")
  }

  def start: Unit = {
    var over = false
  }
}
