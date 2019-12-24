package com.netsnake.server

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.io.{IO, Udp}
import akka.util.ByteString
import com.netsnake.engine.GameOutput.GameState
import com.netsnake.engine.{Game, GameOutput, Point, Snake, SnakeState, Waiting, Running, Dead, Up, Down, Left, Right}

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
        case join: Join =>
          game ! join
        case quit: Quit =>
          game ! quit
        case restart: Restart =>
          game ! restart
        case mcmd: MoveCommand =>
          log.info("Move command received")
          game ! mcmd
        case _ =>
          log.info("Invalid command received")
      }
    case Udp.Unbind =>
      log.info("Unbind received")
      client ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
    case s: GameOutput.GameState =>
      val (remotes, state) = ServerResponse.fromGameOutput(s)
      for {
        (remote, idx) <- remotes
      } client ! Udp.Send(ByteString(s"${idx}_${state}"), remote)
  }
}


object ServerCommand {
  import com.netsnake.engine.GameInput._
  def fromMessage(msg: String, remote: InetSocketAddress): GameCommand = msg match {
    case "s" => Start(remote)
    case "j" => Join(remote)
    case "q" => Quit(remote)
    case "r" => Restart(remote)
    case "mu" => MoveCommand(remote, Up)
    case "md" => MoveCommand(remote, Down)
    case "ml" => MoveCommand(remote, Left)
    case "mr" => MoveCommand(remote, Right)
    case _ => Invalid
  }

}

object ServerResponse {
  val globalSep = "#"
  val playerSep = "|"
  def fromGameOutput(gs: GameState): (List[(InetSocketAddress, Int)], String) = {
    val remotes = gs.playerStates.foldLeft(Nil: List[InetSocketAddress]) {
      (out, ps) => out :+ ps._1 
    }
    val state = gs.playerStates.foldLeft(s"${gs.playerStates.size}${globalSep}${serializeApple(gs.apple)}") {
      (out, pStates) => 
        pStates match {
          case (_, ps) => s"${out}${globalSep}${serializeSnakeState(ps.state)}${playerSep}${ps.score}${playerSep}${serializeSnake(ps.snake)}"
          case _ => out
        }        
    }
    (remotes.zipWithIndex, s"$state\n")
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
        case Point(0, 1) => "d"
        case Point(0, -1) => "u"
        case _ => "i"
      }
        (p, s"${acc}${nextPos}")
    }._2
  }

  def serializeSnakeState(s: SnakeState): Char = s match {
    case Dead => 'd'
    case Running => 'r'
    case Waiting => 'w'
    case _ => 'd'
  }

  def serializeApple(a: Point): String = {
    s"${a.x},${a.y}"
  }
}

object Sender {
  def props(remote: InetSocketAddress): Props = Props(new GameServer(remote))
}

