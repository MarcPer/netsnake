require 'gosu'
require 'socket'
require_relative 'client'
require_relative 'selectable_queue'

class Game < Gosu::Window
  HEAD_COLOR = Gosu::Color::GREEN
  TAIL_COLOR = Gosu::Color::BLUE
  SCALING_FACTOR = 20

  def initialize
    super 40 * SCALING_FACTOR, 40 * SCALING_FACTOR
    self.caption = "Net snake Ruby"
    @font = Gosu::Font.new(25)
    @snake = []
    @server_queue = SelectableQueue.new # holds state received from server; read by game
    @input_queue = SelectableQueue.new # holds input to be sent to server; read by client

    @server_thread = Thread.new do
      client = Client.new(@server_queue, @input_queue, "127.0.0.1", 3000)
      client.start
    end
  end

  def draw
    if @game_over
      @font.draw("Game over", 15 * SCALING_FACTOR, 20 * SCALING_FACTOR, 1, 2.0, 2.0, Gosu::Color::YELLOW)
    end
    @font.draw("Score: #{@score}", 10, 10, 1, 1.0, 1.0, Gosu::Color::YELLOW)
    @snake.each_with_index do |seg, idx|
      next if seg.nil? || seg.empty?
      kind = idx == 0 ? :head : :tail
      draw_snake_segment(seg[0], seg[1], kind)
    end
    draw_apple
  end

  def update
    return if @game_over
    data = @server_queue.empty? ? nil : @server_queue.pop
    @new_direction ||= read_input
    if @new_direction && @input_queue.empty?
      @input_queue << @new_direction
      @new_direction = nil
    end
    read_state(data) if data
  end

  def draw_snake_segment(x, y, kind = :tail)
    color = kind == :head ? HEAD_COLOR : TAIL_COLOR
    Gosu.draw_quad(
      x * SCALING_FACTOR, y * SCALING_FACTOR, color,
      (x + 1) * SCALING_FACTOR, y * SCALING_FACTOR, color,
      (x + 1) * SCALING_FACTOR, (y + 1) * SCALING_FACTOR, color,
      x * SCALING_FACTOR, (y + 1) * SCALING_FACTOR, color
    )
  end

  def draw_apple
    return if [@apple_x, @apple_y].none?
    Gosu.draw_quad(
      @apple_x * SCALING_FACTOR, @apple_y * SCALING_FACTOR, Gosu::Color::RED,
      (@apple_x + 1) * SCALING_FACTOR, @apple_y * SCALING_FACTOR, Gosu::Color::RED,
      (@apple_x + 1) * SCALING_FACTOR, (@apple_y + 1) * SCALING_FACTOR, Gosu::Color::RED,
      @apple_x * SCALING_FACTOR, (@apple_y + 1) * SCALING_FACTOR, Gosu::Color::RED
    )
  end

  def read_state(data)
    state = data.split('|')
    @apple_x, @apple_y = state[0].split(',').map(&:to_i)
    @game_over = state[1] != 'a1'
    @score = state[2].to_i
    return if @game_over
    @snake = build_snake(state[3]) || []
  end

  def build_snake(snake_data)
    pos, @dir, tail = snake_data.split('_')
    snake = []
    snake << pos.split(',').map(&:to_i)
    return @snake if snake.empty?
    tail.each_char do |c|
      new_pos = case c
      when 'l' then [snake.last[0] - 1, snake.last[1]]
      when 'r' then [snake.last[0] + 1, snake.last[1]]
      when 'u' then [snake.last[0], snake.last[1] - 1]
      when 'd' then [snake.last[0], snake.last[1] + 1]
      end
      snake << new_pos if new_pos
    end
    snake
  end

  def read_input
    if move_left? then 'ml'
    elsif move_right? then 'mr'
    elsif move_up? then 'mu'
    elsif move_down? then 'md'
    else nil
    end
  end

  def move_left?
    ['U', 'D', nil].include?(@dir) && Gosu.button_down?(Gosu::KB_LEFT)
  end

  def move_right?
    ['U', 'D', nil].include?(@dir) && Gosu.button_down?(Gosu::KB_RIGHT)
  end

  def move_up?
    ['L', 'R', nil].include?(@dir) && Gosu.button_down?(Gosu::KB_UP)
  end

  def move_down?
    ['L', 'R', nil].include?(@dir) && Gosu.button_down?(Gosu::KB_DOWN)
  end
end

Game.new.show
