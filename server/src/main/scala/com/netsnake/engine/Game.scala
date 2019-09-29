package com.netsnake.engine

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import com.netsnake.engine.GameOutput.{GameState, PlayerState}

import scala.util.Random

object Game {
  def props: Props = Props(new Game)
}

object GameOutput {
  trait GameResponse
  case class GameState(playerStates: Map[InetSocketAddress, PlayerState], apple: Point) extends GameResponse
  case class PlayerState(score: Int, snake: Snake, var alive: Boolean)
  def initialPlayerState: PlayerState = {
    val initSnake = Snake(Point(20, 20) :: Point(21, 20) :: Point(22, 20) :: Point(23, 20) :: Point(24, 20) :: Point(25, 20) :: Nil, Left)
    PlayerState(0, initSnake, true)
  }
  def initialGameState: GameState = GameState(Map(), Point(0,0))
}

final case class Point(x: Int, y: Int) {
  def +(other: Point) = Point(this.x + other.x, this.y + other.y)
  def -(other: Point) = Point(this.x - other.x, this.y - other.y)
  def ==(other: Point) = other.x == x && other.y == y
}

object GameInput {
  trait GameCommand
  case object Cycle
  case object GameOver
  case class MoveCommand(playerId: InetSocketAddress, dir: Direction) extends GameCommand
  case object Invalid extends GameCommand
  case class Start(playerId: InetSocketAddress) extends GameCommand
  case class AddPlayer(playerId: InetSocketAddress) extends GameCommand
}


trait Direction
trait UpDown extends Direction
trait LeftRight extends Direction
case object Up extends UpDown
case object Down extends UpDown
case object Right extends LeftRight
case object Left extends LeftRight

case class Snake(var pos: List[Point], var direction: Direction)

class Game extends Actor with ActorLogging {
  val height = 32
  val width = 32
  import GameInput._
  private var players = Map[InetSocketAddress, PlayerState]() // Maps remote address to index in snakes and scores Lists
  private var deadPlayers = Set[InetSocketAddress]()
  private var apple: Option[Point] = Some(newApple)

  def active: Receive = {
    case Cycle => cycleGame
    case MoveCommand(pid, dir) =>
      val newDir = changeDirection(players(pid).snake.direction, dir)
      players(pid).snake.direction = newDir
    case GameOver => context.become(inactive)
    case cmd => log.info(s"Received: $cmd")
  }

  def inactive: Receive = {
    case Start(playerId) =>
      players = addPlayer(playerId, players)
      context.become(active)
      self ! Cycle
  }

  def receive = inactive

  def cycleGame: Unit = {
    for { p <- deadPlayers } { players -= p}
    for { p <- players.values } move(p)
    for { p <- collidedPlayers(players) } killPlayer(p)
    for { p <- outOfBoundsPlayers(players) } killPlayer(p)
    apple match {
      case None => apple = Some(newApple)
      case _ =>
    }

    if (players.size < 1)
      self ! GameOver
    else {
      broadcastState
      Thread.sleep(1000)
      self ! Cycle
    }
  }

  def addPlayer(id: InetSocketAddress, players: Map[InetSocketAddress, PlayerState]) =
    players + (id -> GameOutput.initialPlayerState)

  // Mark player as dead, but don't immediately remove from players list,
  // so they still get one broadcast informing they are dead.
  def killPlayer(id: InetSocketAddress) = {
    players(id).alive = false
    deadPlayers = deadPlayers + id
  }

  def changeDirection(currDir: Direction, dir: Direction): Direction = (currDir, dir) match {
      case (_: UpDown, _: LeftRight) => dir
      case (_: LeftRight, _: UpDown) => dir
      case _ => currDir
  }

  def collidedPlayers(players: Map[InetSocketAddress, PlayerState]): Set[InetSocketAddress] = {
    val positionTuples = for {
      (id, state) <- players
      pos <- state.snake.pos
    } yield(pos, id, 0) // the last element is there to make sure Scala doesn't de-duplicate the pos keys

    val positions = positionTuples.foldLeft(Map[Point, List[InetSocketAddress]]()) { case (out, (pos, id, _)) =>
        out + (pos -> (id :: out.getOrElse(pos, Nil)))
    }

    positions.foldLeft(Set[InetSocketAddress]()) { case (out, (_, ids)) =>
      if (ids.size > 1)
        out ++ ids
      else
        out
    }
  }

  def outOfBoundsPlayers(players: Map[InetSocketAddress, PlayerState]): Set[InetSocketAddress] = {
    val heads = for {
      (id, state) <- players
    } yield(id, state.snake.pos.head)
    heads.foldLeft(Set[InetSocketAddress]()) { case (out, (id, head)) =>
      if (head.x < 0 || head.x > width || head.y < 0 || head.y > height) out + id else out
    }
  }

  def newApple: Point = {
    val filledPositions = for {
      player <- players
      pos <- player._2.snake.pos
    } yield pos

    applePos(filledPositions.toSet, new Random())
  }

  def applePos(pos: Set[Point], random: Random) : Point = {
    val p = Point(random.nextInt(width), random.nextInt(height))
    if (pos(p)) applePos(pos, random) else p
  }

  def move(state: GameOutput.PlayerState) = {
    val newPos = state.snake.direction match {
      case Up => Point(0, 1) + state.snake.pos.head
      case Down => Point(0, -1) + state.snake.pos.head
      case Right => Point(1, 0) + state.snake.pos.head
      case Left => Point(-1, 0) + state.snake.pos.head
    }
    if (newPos == apple.getOrElse(Point(-1, -1))) {
      apple = None
      state.snake.pos = newPos :: state.snake.pos
    }
    else
      state.snake.pos = newPos :: state.snake.pos.dropRight(1)
  }

  def broadcastState: Unit = {
    context.parent ! GameState(players, apple.getOrElse(Point(0,0)))
  }
}
