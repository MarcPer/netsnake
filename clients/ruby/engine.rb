require 'gosu'
require 'socket'
require_relative 'udp_client'
require_relative 'selectable_queue'

class Engine < Gosu::Window
  HEAD_COLOR = Gosu::Color::GREEN
  TAIL_COLOR = Gosu::Color::BLUE
  HEAD_COLOR_OTHER = Gosu::Color::FUCHSIA
  TAIL_COLOR_OTHER = Gosu::Color::GRAY
  AI_PLAN_COLOR = Gosu::Color::YELLOW
  SCALING_FACTOR = 20
  ARENA_WIDTH = 40
  ARENA_HEIGHT = 40

  attr_reader :ai, :debug_mode

  def initialize(host, port = 3000, ai_controlled = false, debug_mode: false)
    super 40 * SCALING_FACTOR, 40 * SCALING_FACTOR
    @ai = if ai_controlled
      require_relative 'ai'
      AI::Controller.new
    else
      nil
    end
    @debug_mode = debug_mode
    self.caption = "Netsnake Ruby client"
    @font = Gosu::Font.new(25)
    @snake = []
    @score = 0
    @other_snakes = []
    @other_scores = []
    @server_queue = SelectableQueue.new # holds state received from server; read by Engine
    @input_queue = SelectableQueue.new # holds input to be sent to server; read by UdpClient

    Thread.abort_on_exception = true
    @server_thread = Thread.new do
      client = UdpClient.new(@server_queue, @input_queue, host, port)
      client.start
    end
  rescue
    finish
    raise
  end

  def draw
    if @game_over
      @font.draw_text("Game over", 15 * SCALING_FACTOR, 20 * SCALING_FACTOR, 1, 2.0, 2.0, Gosu::Color::YELLOW)
      @font.draw_text("Press space to restart", 14 * SCALING_FACTOR, 22 * SCALING_FACTOR, 1, 1.2, 1.2, Gosu::Color::YELLOW)
    end
    @font.draw_text("Score: #{@score}", 10, 10, 1, 1.0, 1.0, Gosu::Color::YELLOW)
    @other_scores.each_with_index do |score, idx|
      @font.draw_text("Score: #{@score}", 10, 10 + idx, 1, 1.0, 1.0, Gosu::Color::YELLOW)
    end
    draw_ai_plan if debug_mode
    draw_snake(@snake, true)
    @other_snakes.each { |s| draw_snake(s, false) }
    draw_apple
  end

  def draw_ai_plan
    return unless @final_node
    curr_node = @final_node
    count = 1000
    while(curr_node&.prev_node && count > 0)
      count -= 1
      x = curr_node.x
      y = curr_node.y
      Gosu.draw_quad(
        x * SCALING_FACTOR, y * SCALING_FACTOR, AI_PLAN_COLOR,
        (x + 1) * SCALING_FACTOR, y * SCALING_FACTOR, AI_PLAN_COLOR,
        (x + 1) * SCALING_FACTOR, (y + 1) * SCALING_FACTOR, AI_PLAN_COLOR,
        x * SCALING_FACTOR, (y + 1) * SCALING_FACTOR, AI_PLAN_COLOR
      )
      curr_node = curr_node.prev_node
    end
  end

  def draw_snake(snake, curr_player)
    snake.each_with_index do |seg, idx|
      next if seg.nil? || seg.empty?
      kind = if curr_player
        idx == 0 ? :head : :tail
      else
        idx == 0 ? :head_other : :tail_other
      end
      draw_snake_segment(seg[0], seg[1], kind)
    end
  end

  def update
    data = @server_queue.empty? ? nil : @server_queue.pop
    new_direction = read_input
    if new_direction == 'r'
      @input_queue << new_direction
      new_direction = nil
    elsif ai && !@game_over && data
      pos = @snake.first
      if pos && (pos[0] != @apple_x || pos[1] != @apple_y)
        obstacles = @snake[1..-1] | @other_snakes.flatten(1)
        best_dir, @final_node, safe_dir = ai.update(pos, @dir.downcase, [@apple_x, @apple_y], obstacles)
        cmd = "m#{best_dir}" if best_dir && best_dir != @dir.downcase
        @input_queue << cmd if cmd
        @input_queue << "m#{safe_dir}" if safe_dir
      end
    elsif new_direction && @input_queue.empty?
      @input_queue << new_direction
      new_direction = nil
    end
    read_state(data) if data
  rescue
    finish
    raise
  end

  def draw_snake_segment(x, y, kind = :tail)
    color = case kind
    when :head then HEAD_COLOR
    when :tail then TAIL_COLOR
    when :head_other then HEAD_COLOR_OTHER
    when :tail_other then TAIL_COLOR_OTHER
    else TAIL_COLOR
    end
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
    payload_size, payload = data.split('::')
    state = payload.split('#')
    @other_snakes = []
    curr_player, num_players = state.shift.split('_').map(&:to_i)
    @apple_x, @apple_y = state.shift.split(',').map(&:to_i)
    state.each_with_index { |s, idx| read_player_state(s, idx == curr_player) }
  end

  def read_player_state(state, curr_player = true)
    player_state = state.split('|')
    unless curr_player
      snake = build_snake(player_state[2], false) || []
      @other_snakes << snake unless snake.empty?
      return
    end
    @game_over = player_state[0] == 'd'
    @score = player_state[1].to_i
    @snake = build_snake(player_state[2], true) || []
  end

  def build_snake(snake_data, curr_player)
    pos, dir, tail = snake_data.split('_')
    @dir = dir if curr_player
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
    elsif restart? then 'r'
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

  def restart?
    Gosu.button_down?(Gosu::KB_SPACE)
  end

  def button_down(id)
    if id == Gosu::KB_ESCAPE
      finish
    else
      super
    end
  end

  def finish
    @input_queue << 'q'
    @server_thread.join
    close
  end
end
