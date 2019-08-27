package com.netsnake.server

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.io.{IO, Udp}
import akka.util.ByteString
import com.netsnake.engine.Game

object GameServer extends App {
  val localAddress: InetSocketAddress = new InetSocketAddress("127.0.0.1", 3000)

  val system = ActorSystem("netsnake")
  val server = system.actorOf(Sender.props(localAddress))

  Thread.sleep(15000)
  server ! Udp.Unbind

  trait ServerCommand { def playerId: String }
  case class StartGame(override val playerId: String) extends ServerCommand
  case class QuitGame(override val playerId: String) extends ServerCommand
}

class GameServer(address: InetSocketAddress) extends Actor with ActorLogging {
  private val games = scala.collection.mutable.Map[String, ActorRef]()
  import context.system
  IO(Udp) ! Udp.Bind(self, address)

  def receive = {
    case Udp.Bound(_) =>
      log.info("Bound")
      context.become(ready(sender()))
  }

  import com.netsnake.engine.GameCommands._
  def ready(client: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      log.info("Received: " + data.utf8String.trim + " from " + remote.getAddress)
      val cmd = ServerCommand.fromMessage(data.utf8String.trim)
      cmd match {
        case Start if !games.contains(remote.getHostName) =>
          val game = context.actorOf(Game.props)
          games(remote.getHostName) = game
        case MoveUp if games.contains(remote.getHostName) =>
          games(remote.getHostName) ! MoveUp
      }

      // echo
      client ! Udp.Send(ByteString(data.utf8String.trim), remote)
    case Udp.Unbind =>
      log.info("Unbind received")
      client ! Udp.Unbind
    case Udp.Unbound => context.stop(self)

  }

//  def parse(msg: ByteString): ServerCommand = {
//    import GameServer._
//    StartGame
//  }
}


object ServerCommand {
  import com.netsnake.engine.GameCommands._
  def fromMessage(msg: String): GameCommand = msg match {
    case "s" => Start
    case "mu" => MoveUp
    case _ => Invalid
  }
  def fromGameCommand(cmd: GameCommand): String = cmd match {
    case MoveUp => "u"
  }

}

object Sender {
  def props(remote: InetSocketAddress): Props = Props(new GameServer(remote))
}
