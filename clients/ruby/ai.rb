# frozen_string_literal: true

require 'set'
require_relative 'engine'
require 'forwardable'

module AI
  OBSTACLE_VAL = 2 * Engine::ARENA_WIDTH * Engine::ARENA_HEIGHT
  class Node
    attr_accessor :prev_node, :dist_from_origin, :dist_to_goal
    attr_reader :x, :y

    def initialize(x, y, dist_to_goal, dist_from_origin = nil)
      @x = x
      @y = y
      @dist_to_goal = dist_to_goal
      @dist_from_origin = dist_from_origin
    end

    def to_s
      "x: #{x}, y: #{y}, dist_from_origin: #{dist_from_origin}, dist_to_goal: #{dist_to_goal}, prev_node_x: #{prev_node&.x}, prev_node_y: #{prev_node&.y}"
    end

    def neighbor_coords
      @neighbor_coords ||= begin
        [
          [x - 1, y],
          [x, y - 1],
          [x + 1, y],
          [x, y + 1]
        ].shuffle.each_with_object([]) do |new_coords, memo|
          memo.push(new_coords) if valid_neighbor?(*new_coords)
        end
      end
    end

    def valid_neighbor?(x, y)
      return false if x < 0 || y < 0 || x >= Engine::ARENA_WIDTH || y >= Engine::ARENA_HEIGHT
      return true unless prev_node
      prev_node.x != x || prev_node.y != y
    end
  end

  class Controller
    def update(c_pos, direction, t_pos, snakes)
      prio_stack = PriorityStack.new
      dist_map = build_bitmap(c_pos, t_pos, snakes)

      # send second direction change to server if heading towards a wall
      safe_move = emergency_move(c_pos, direction, t_pos)

      # starting node
      start_node = Node.new(c_pos[0], c_pos[1], OBSTACLE_VAL, 0)
      curr_node = start_node

      counts = 1000
      while (counts > 0 && curr_node && curr_node.dist_to_goal > 0)
        counts -= 1
        curr_node.neighbor_coords.each do |x, y|
          neigh_node = dist_map[x + Engine::ARENA_WIDTH * y]
          next if neigh_node.nil? || neigh_node == curr_node
          acc_dist = curr_node.dist_from_origin + 1 + neigh_node.dist_to_goal

          neigh_node.dist_from_origin = curr_node.dist_from_origin + 1
          neigh_node.prev_node = curr_node unless neigh_node.prev_node == start_node
          prio_stack.push(acc_dist, neigh_node)
        end

        curr_node = prio_stack.pop
      end

      final_node = curr_node.dup # used for drawing the plan on debug_mode
      last_node = nil
      counts = Engine::ARENA_WIDTH * Engine::ARENA_WIDTH
      while (counts > 0 && curr_node&.prev_node)
        counts -= 1
        last_node = curr_node
        curr_node = curr_node.prev_node
      end

      delta_x = last_node.x - c_pos[0]
      delta_y = last_node.y - c_pos[1]
      best_move = case [(last_node.x - c_pos[0]), (last_node.y - c_pos[1])]
      when [-1, 0] then 'l'
      when [1, 0] then 'r'
      when [0, -1] then 'u'
      when [0, 1] then 'd'
      end
      return [best_move, final_node, safe_move] if best_move

      best_move ||= if %(u d).include?(direction)
                    delta_x > 0 ? 'r' : 'l'
                  else
                    delta_y > 0 ? 'd' : 'u'
                  end
      best_move = nil unless safe?(best_move, c_pos, dist_map)
      [best_move, final_node, safe_move]
    end

    def safe?(best_move, c_pos, dist_map)
      x = c_pos[0]
      y = c_pos[1]
      case best_move
      when 'r'
        return false if x + 1 >= Engine::ARENA_WIDTH
        dist_map[x + 1 + y * Engine::ARENA_WIDTH].dist_to_goal != OBSTACLE_VAL
      when 'l'
        return false if x - 1 <= 0
        dist_map[x - 1 + y * Engine::ARENA_WIDTH].dist_to_goal != OBSTACLE_VAL
      when 'u'
        return false if y - 1 <= 0
        dist_map[x + (y - 1) * Engine::ARENA_WIDTH].dist_to_goal != OBSTACLE_VAL
      when 'd'
        return false if y + 1 >= Engine::ARENA_HEIGHT
        dist_map[x + (y + 1) * Engine::ARENA_WIDTH].dist_to_goal != OBSTACLE_VAL
      end
    end

    def emergency_move(c_pos, direction, t_pos)
      safe_directions = %w(l r d u)
      safe_directions -= ['l'] if c_pos[0] <= 1
      safe_directions -= ['r'] if c_pos[0] >= Engine::ARENA_WIDTH - 2
      safe_directions -= ['u'] if c_pos[1] <= 1
      safe_directions -= ['d'] if c_pos[1] >= Engine::ARENA_HEIGHT - 2

      # Snake is not in unsafe situation
      return nil if safe_directions.size == 4 || safe_directions.include?(direction)

      available_directions(safe_directions, direction).sample
    end

    def available_directions(dirs, curr_dir)
      if %w(l r).include?(curr_dir)
        dirs & %w(u d)
      else
        dirs & %w(l r)
      end
    end

    def build_bitmap(curr_pos, target_pos, snakes)
      bm = Bitmap.new(curr_pos, target_pos)
      return bm unless snakes

      # obstacles will get an effective high distance to goal
      snakes.each do |x, y|
        n = Node.new(x, y, OBSTACLE_VAL)
        bm[x + Engine::ARENA_WIDTH * y] = n
        n.neighbor_coords.each do |xn, yn|
          bm[xn + Engine::ARENA_WIDTH * yn] ||= Node.new(xn, yn, OBSTACLE_VAL/4)
        end
      end

      target_node = Node.new(target_pos[0], target_pos[1], 0)
      bm[target_pos[0] + Engine::ARENA_WIDTH * target_pos[1]] = target_node

      # Avoid getting close to the walls
      (0..Engine::ARENA_WIDTH - 1).each do |x|
        y = 0
        bm[x + Engine::ARENA_WIDTH * y] ||= Node.new(x, y, OBSTACLE_VAL/4)
        y = Engine::ARENA_HEIGHT - 1
        bm[x + Engine::ARENA_WIDTH * y] ||= Node.new(x, y, OBSTACLE_VAL/4)
      end

      (0..Engine::ARENA_HEIGHT - 1).each do |y|
        x = 0
        bm[x + Engine::ARENA_WIDTH * y] ||= Node.new(x, y, OBSTACLE_VAL/4)
        x = Engine::ARENA_WIDTH - 1
        bm[x + Engine::ARENA_WIDTH * y] ||= Node.new(x, y, OBSTACLE_VAL/4)
      end

      neigh_obst = target_node.neighbor_coords.count do |x, y|
        bm[x + Engine::ARENA_WIDTH * y]&.dist_to_goal == OBSTACLE_VAL
      end
      if neigh_obst >= 2
        loop do
          x = rand(Engine::ARENA_WIDTH)
          y = rand(Engine::ARENA_HEIGHT)
          return build_bitmap(curr_pos, [x, y], snakes) unless bm[x + Engine::ARENA_WIDTH * y]&.dist_to_goal&.>=(OBSTACLE_VAL/5)
        end
      end

      # for remaining positions, calculate distance to goal
      (0..Engine::ARENA_WIDTH - 1).each do |x|
        (0..Engine::ARENA_HEIGHT - 1).each do |y|
          next if bm[x + Engine::ARENA_WIDTH * y]
          dist = (x - target_pos[0]).abs + (y - target_pos[1]).abs
          bm[x + Engine::ARENA_WIDTH * y] = Node.new(x, y, dist)
        end
      end
      bm
    end
  end

  class Bitmap
    extend Forwardable
    def_delegators :@bm, :[], :[]=

    attr_reader :curr_pos, :target_pos
    def initialize(curr_pos, target_pos)
      @curr_pos = curr_pos
      @target_pos = target_pos
      @bm = []
    end

    def print_map
      print(' ' * 6)
      (0..Engine::ARENA_WIDTH - 1).each do |x|
        printf('%04d ', x)
      end
      puts(' ')
      (0..Engine::ARENA_HEIGHT - 1).each do |y|
        printf('%04d |', y)
        (0..Engine::ARENA_WIDTH - 1).each do |x|
          if curr_pos[0] == x && curr_pos[1] == y
            print('HEAD ')
          elsif target_pos[0] == x && target_pos[1] == y
            print('TRGT ')
          else
            printf('%04d ', @bm[x + Engine::ARENA_WIDTH * y]&.dist_to_goal)
          end
        end
        puts ''
      end
    end
  end

  class PriorityStack
    def initialize
      @prio_array = []
      @curr_prios = Set.new
    end

    def push(priority, node)
      @prio_array[priority] ||= []
      @curr_prios.add(priority)
      arr = @prio_array[priority]
      arr.push(node) unless arr.include?(node)
    end

    def pop
      return nil if @curr_prios.empty?
      lowest_prio = @curr_prios.min
      node = @prio_array[lowest_prio].pop
      @curr_prios.delete(lowest_prio) if @prio_array[lowest_prio].empty?
      node
    end
  end
end
