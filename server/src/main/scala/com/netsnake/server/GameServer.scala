package com.netsnake.server

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.io.{IO, Udp}
import akka.util.ByteString
import com.netsnake.engine.GameOutput.{GameResponse, GameState}
import com.netsnake.engine.{Game, GameOutput, Point, Snake, Up, Down, Left, Right}

object GameServer extends App {
  val localAddress: InetSocketAddress = new InetSocketAddress("127.0.0.1", 3000)

  val system = ActorSystem("netsnake")
  val server = system.actorOf(Sender.props(localAddress))

  Thread.sleep(15000)
  // server ! Udp.Unbind

  trait ServerCommand { def playerId: String }
  case class StartGame(override val playerId: String) extends ServerCommand
  case class QuitGame(override val playerId: String) extends ServerCommand
}

class GameServer(address: InetSocketAddress) extends Actor with ActorLogging {
  val game = context.actorOf(Game.props)

  import context.system
  IO(Udp) ! Udp.Bind(self, address)

  def receive = {
    case Udp.Bound(_) =>
      log.info("Bound")
      context.become(ready(sender()))
  }

  import com.netsnake.engine.GameInput._

  def ready(client: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      log.info("Received: " + data.utf8String.trim + " from " + remote.getAddress)
      val cmd = ServerCommand.fromMessage(data.utf8String.trim, remote)
      cmd match {
        case start: Start =>
          game ! start
        case mcmd: MoveCommand =>
          log.info("Move command received")
          game ! mcmd
        case _ =>
          log.info("Invalid command received")
      }

      // echo
      client ! Udp.Send(ByteString(data.utf8String.trim), remote)
    case Udp.Unbind =>
      log.info("Unbind received")
      client ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
    case s: GameOutput.GameState =>
      val stateMap = ServerResponse.fromGameOutput(s)
      for {
        (remote, playerState) <- stateMap
      } client ! Udp.Send(ByteString(playerState), remote)
  }
}


object ServerCommand {
  import com.netsnake.engine.GameInput._
  def fromMessage(msg: String, remote: InetSocketAddress): GameCommand = msg match {
    case "s" => Start(remote)
    case "mu" => MoveCommand(remote, Up)
    case "md" => MoveCommand(remote, Down)
    case "ml" => MoveCommand(remote, Left)
    case "mr" => MoveCommand(remote, Right)
    case _ => Invalid
  }

}

object ServerResponse {
  def fromGameOutput(cmd: GameResponse): Map[InetSocketAddress, String] = cmd match {
    case GameState(playerStates, apple) => for {
      (remote, ps) <- playerStates
    } yield (remote, s"a${if (ps.alive) 1 else 0}|${ps.score}|${serializeApple(apple)}|${serializeSnake(ps.snake)}\n")
  }

  def serializeSnake(s: Snake): String = {
    val headString = s"${s.pos.head.x},${s.pos.head.y}"
    val dirString = s.direction match {
      case Up => "U"
      case Down => "D"
      case Right => "R"
      case Left => "L"
    }
    s.pos.tail.foldLeft((s.pos.head, s"${headString}_${dirString}_")) { case ((lastPos, acc), p) =>
      val nextPos = (p - lastPos) match {
        case Point(1, 0) => "r"
        case Point(-1, 0) => "l"
        case Point(0, 1) => "u"
        case Point(0, -1) => "d"
        case _ => "i"
      }
        (p, s"${acc}${nextPos}")
    }._2
  }

  def serializeApple(a: Point): String = {
    s"${a.x},${a.y}"
  }
}

object Sender {
  def props(remote: InetSocketAddress): Props = Props(new GameServer(remote))
}

object BiMap {
  private[BiMap] trait MethodDistinctor
  implicit object MethodDistinctor extends MethodDistinctor
}

case class BiMap[X, Y](var map: Map[X, Y]) {
  def this(tuples: (X,Y)*) = this(tuples.toMap)
  private var reverseMap = map map (_.swap)
  require(map.size == reverseMap.size, "no 1 to 1 relation")
  def apply(x: X): Y = map(x)
  def apply(y: Y)(implicit d: BiMap.MethodDistinctor): X = reverseMap(y)
  val domain = map.keys
  val codomain = reverseMap.keys
  def values = map.values
  def keys = map.keys

  def ++(other: Map[X,Y]) = {
    this.map = this.map ++ other
    val otherReverse = other map (_.swap)
    this.reverseMap = this.reverseMap ++ otherReverse
  }

  def --(other: Map[X,Y]) = {
    this.map = this.map -- other.keys
    this.reverseMap = this.reverseMap -- other.values
  }

  def contains(x: X): Boolean = map.contains(x)
  def contains(y: Y)(implicit d: BiMap.MethodDistinctor): Boolean = reverseMap.contains(y)
}

//val biMap = new BiMap(1 -> "A", 2 -> "B")
//println(biMap(1)) // A
//println(biMap("B")) // 2
