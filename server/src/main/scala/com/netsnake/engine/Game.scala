package com.netsnake.engine

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import com.netsnake.engine.GameOutput.{GameState, PlayerState}

import scala.util.Random

object Game {
  def props: Props = Props(new Game)
}

object GameOutput {
  case class GameState(playerStates: Map[InetSocketAddress, PlayerState], apple: Point)
  case class PlayerState(var score: Int, snake: Snake, var state: SnakeState)
  def initialPlayerState(state: SnakeState): PlayerState = {
    val initSnake = Snake(Point(20, 20) :: Point(21, 20) :: Point(22, 20) :: Point(23, 20) :: Point(24, 20) :: Point(25, 20) :: Nil, Left, 0)
    PlayerState(0, initSnake, state)
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
  case class Restart(playerId: InetSocketAddress) extends GameCommand
  case class Join(playerId: InetSocketAddress) extends GameCommand
  case class Quit(playerId: InetSocketAddress) extends GameCommand
}


trait Direction
trait UpDown extends Direction
trait LeftRight extends Direction
case object Up extends UpDown
case object Down extends UpDown
case object Right extends LeftRight
case object Left extends LeftRight

case class Snake(var pos: List[Point], var direction: Direction, var grow: Int)
trait SnakeState
case object Waiting extends SnakeState
case object Running extends SnakeState
case object Dead extends SnakeState

class Game extends Actor with ActorLogging {
  val height = 40
  val width = 40
  import GameInput._
  private var players = Map[InetSocketAddress, PlayerState]() // Maps remote address to index in snakes and scores Lists
  private var quitPlayers = Set[InetSocketAddress]()
  private var apple: Option[Point] = Some(newApple)

  def active: Receive = {
    case Cycle => cycleGame
    case Join(playerId) if (!players.contains(playerId)) =>
      players = addPlayer(playerId, players, Running)
      log.info(s"Num. players: ${players.size}")
    case Start(playerId) if (!players.contains(playerId)) =>
      players = addPlayer(playerId, players, Running)
      log.info(s"Num. players: ${players.size}")
    case Restart(playerId) if (players.contains(playerId) && players(playerId).state == Dead) =>
      players -= playerId
      players = addPlayer(playerId, players, Running)
    case Quit(playerId) if (players.contains(playerId)) =>
      quitPlayer(playerId)
    case MoveCommand(pid, dir) =>
      val newDir = changeDirection(players(pid).snake.direction, dir)
      players(pid).snake.direction = newDir
    case GameOver => context.become(inactive)
    case cmd => log.info(s"Received: $cmd")
  }

  def inactive: Receive = {
    case Join(playerId) =>
      players = addPlayer(playerId, players, Waiting)
      log.info(s"Num. players: ${players.size}")
    case Start(playerId) =>
      if (players.contains(playerId)) {
        players(playerId).state = Running
      } else {
        players = addPlayer(playerId, players, Running)
      }
      log.info(s"Num. players: ${players.size}")
      context.become(active)
      self ! Cycle
  }

  def receive = inactive

  def cycleGame: Unit = {
    for { p <- quitPlayers } { players -= p}
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
      Thread.sleep(200)
      self ! Cycle
    }
  }

  def addPlayer(id: InetSocketAddress, players: Map[InetSocketAddress, PlayerState], state: SnakeState) =
    players + (id -> GameOutput.initialPlayerState(state))

  def quitPlayer(id: InetSocketAddress) = {
    quitPlayers = quitPlayers + id
  }

  // Mark player as dead, but don't immediately remove from players list,
  // so they still get one broadcast informing they are dead.
  def killPlayer(id: InetSocketAddress) = {
    players(id).state = Dead
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
      if (head.x < 0 || head.x >= width || head.y < 0 || head.y >= height) out + id else out
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
    if (state.state == Dead) state.snake.pos
    else {
      val newPos = state.snake.direction match {
        case Up => Point(0, -1) + state.snake.pos.head
        case Down => Point(0, 1) + state.snake.pos.head
        case Right => Point(1, 0) + state.snake.pos.head
        case Left => Point(-1, 0) + state.snake.pos.head
      }
      if (newPos == apple.getOrElse(Point(-1, -1))) {
        apple = None
        state.score += 1
        state.snake.grow += 3
      }
      state.snake.pos = newPos :: state.snake.pos

      if (state.snake.grow <= 0)
        state.snake.pos = state.snake.pos.dropRight(1)
      else
        state.snake.grow -= 1
    }
  }

  def broadcastState: Unit = {
    context.parent ! GameState(players, apple.getOrElse(Point(0,0)))
  }
}
