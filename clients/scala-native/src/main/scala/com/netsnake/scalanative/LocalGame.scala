package com.netsnake.engine

import scalanative.native._
import SDL._
import SDLExtra._

@extern
@link("SDL2")
object SDL {
  type Window   = CStruct0
  type Renderer = CStruct0

  def SDL_Init(flags: UInt): Unit = extern
  def SDL_CreateWindow(title: CString,
                       x: CInt,
                       y: CInt,
                       w: Int,
                       h: Int,
                       flags: UInt): Ptr[Window] = extern
  def SDL_Delay(ms: UInt): Unit                  = extern
  def SDL_CreateRenderer(win: Ptr[Window],
                         index: CInt,
                         flags: UInt): Ptr[Renderer] = extern

  type _56   = Nat.Digit[Nat._5, Nat._6]
  type Event = CStruct2[UInt, CArray[Byte, _56]]

  def SDL_PollEvent(event: Ptr[Event]): CInt = extern

  type Rect = CStruct4[CInt, CInt, CInt, CInt]

  def SDL_RenderClear(renderer: Ptr[Renderer]): Unit = extern
  def SDL_SetRenderDrawColor(renderer: Ptr[Renderer],
                             r: UByte,
                             g: UByte,
                             b: UByte,
                             a: UByte): Unit = extern
  def SDL_RenderFillRect(renderer: Ptr[Renderer], rect: Ptr[Rect]): Unit =
    extern
  def SDL_RenderPresent(renderer: Ptr[Renderer]): Unit = extern

  type KeyboardEvent =
    CStruct8[UInt, UInt, UInt, UByte, UByte, UByte, UByte, Keysym]
  type Keysym   = CStruct4[Scancode, Keycode, UShort, UInt]
  type Scancode = Int
  type Keycode  = Int
}

object SDLExtra {
  val INIT_VIDEO   = 0x00000020.toUInt
  val WINDOW_SHOWN = 0x00000004.toUInt
  val VSYNC        = 0x00000004.toUInt

  implicit class EventOps(val self: Ptr[Event]) extends AnyVal {
    def type_ = !(self._1)
  }

  val QUIT_EVENT = 0x100.toUInt

  implicit class RectOps(val self: Ptr[Rect]) extends AnyVal {
    def init(x: Int, y: Int, w: Int, h: Int): Ptr[Rect] = {
      !(self._1) = x
      !(self._2) = y
      !(self._3) = w
      !(self._4) = h
      self
    }
  }

  val KEY_DOWN  = 0x300.toUInt
  val KEY_UP    = (0x300 + 1).toUInt
  val RIGHT_KEY = 1073741903
  val LEFT_KEY  = 1073741904
  val DOWN_KEY  = 1073741905
  val UP_KEY    = 1073741906

  implicit class KeyboardEventOps(val self: Ptr[KeyboardEvent])
    extends AnyVal {
    def keycode: Keycode = !(self._8._2)
  }
}

final case class Point(x: Int, y: Int) {
  def -(other: Point) = Point(this.x - other.x, this.y - other.y)
  def +(other: Point) = Point(this.x - other.x, this.y - other.y)
}

object ScalaClient extends App {
  var window: Ptr[Window]     = _
  var renderer: Ptr[Renderer] = _
  var snake: List[Point]      = _
  var pressed                 = collection.mutable.Set.empty[Keycode]
  var over                    = false
  var apple                   = Point(0, 0)
  var rand                    = new java.util.Random

  val title  = c"Snake"
  val width  = 800
  val height = 800
  val Up     = Point(0, 1)
  val Down   = Point(0, -1)
  val Left   = Point(1, 0)
  val Right  = Point(-1, 0)

  def newApple(): Point = {
    var pos = Point(0, 0)
    do {
      pos = Point(rand.nextInt(40), rand.nextInt(40))
    } while (snake.exists(_ == pos))
    pos
  }

  def drawColor(r: UByte, g: UByte, b: UByte): Unit =
    SDL_SetRenderDrawColor(renderer, r, g, b, 0.toUByte)
  def drawClear(): Unit =
    SDL_RenderClear(renderer)
  def drawPresent(): Unit =
    SDL_RenderPresent(renderer)
  def drawSquare(point: Point) = {
    val rect = stackalloc[Rect].init(point.x * 20, point.y * 20, 20, 20)
    SDL_RenderFillRect(renderer, rect)
  }
  def drawSnake(): Unit = {
    val head :: tail = snake
    drawColor(100.toUByte, 200.toUByte, 100.toUByte)
    drawSquare(head)
    drawColor(0.toUByte, 150.toUByte, 0.toUByte)
    tail.foreach(drawSquare)
  }
  def drawApple(): Unit = {
    drawColor(150.toUByte, 0.toUByte, 0.toUByte)
    drawSquare(apple)
  }

  def onDraw(): Unit = {
    drawColor(0.toUByte, 0.toUByte, 0.toUByte)
    drawClear()
    drawSnake()
    drawApple()
    drawPresent()
  }

  def gameOver(): Unit = {
    over = true
    println(s"Game is over, your score is: " + snake.length)
  }

  def move(newPos: Point) =
    if (!over) {
      if (newPos.x < 0 || newPos.y < 0 || newPos.x > 39 || newPos.y > 39) {
        println("out of bounds")
        gameOver()
      } else if (snake.exists(_ == newPos)) {
        println("hit itself")
        gameOver()
      } else if (apple == newPos) {
        snake = newPos :: snake
        apple = newApple()
      } else {
        snake = newPos :: snake.init
      }
    }

  def onIdle(): Unit = {
    val head :: second :: rest = snake
    val direction              = second - head
    val userDirection =
      if (pressed.contains(UP_KEY)) Up
      else if (pressed.contains(DOWN_KEY)) Down
      else if (pressed.contains(LEFT_KEY)) Left
      else if (pressed.contains(RIGHT_KEY)) Right
      else direction
    move(head + userDirection)
  }

  def init(): Unit = {
    rand.setSeed(java.lang.System.nanoTime)
    SDL_Init(INIT_VIDEO)
    window = SDL_CreateWindow(title, 0, 0, width, height, WINDOW_SHOWN)
    renderer = SDL_CreateRenderer(window, -1, VSYNC)
    snake = Point(10, 10) :: Point(9, 10) :: Point(8, 10) :: Point(7, 10) :: Nil
    apple = newApple()
  }

  def delay(ms: UInt): Unit =
    SDL_Delay(ms)

  def loop(): Unit = {
    val event = stackalloc[Event]
    while (true) {
      while (SDL_PollEvent(event) != 0) {
        event.type_ match {
          case QUIT_EVENT =>
            return
          case KEY_DOWN =>
            pressed += event.cast[Ptr[KeyboardEvent]].keycode
          case KEY_UP =>
            pressed -= event.cast[Ptr[KeyboardEvent]].keycode
          case _ =>
            ()
        }
      }
      onDraw()
      onIdle()
      delay((1000 / 12).toUInt)
    }
  }

  init()
  loop()
}
