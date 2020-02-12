# frozen_string_literal: true

require 'set'
require_relative 'engine'

module AI
  OBSTACLE_VAL = 255
  class Node
    attr_accessor :prev_node, :dist_from_origin
    attr_reader :x, :y, :dist_to_goal

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
    def update(c_pos, t_pos, snakes)
      prio_stack = PriorityStack.new
      dist_map = build_bitmap(t_pos, snakes)

      # starting node
      curr_node = Node.new(c_pos[0], c_pos[1], OBSTACLE_VAL, 0)

      counts = 1000
      while (counts > 0 && curr_node && curr_node.dist_to_goal > 0)
        counts -= 1
        curr_node.neighbor_coords.each do |x, y|
          neigh_node = dist_map[x + Engine::ARENA_WIDTH * y]
          next if neigh_node.nil? || neigh_node == curr_node
          acc_dist = curr_node.dist_from_origin + 1 + neigh_node.dist_to_goal

          neigh_node.dist_from_origin = curr_node.dist_from_origin + 1
          neigh_node.prev_node = curr_node
          prio_stack.push(acc_dist, neigh_node)
        end

        curr_node = prio_stack.pop
      end

      last_node = nil
      counts = Engine::ARENA_WIDTH * Engine::ARENA_WIDTH
      while (curr_node&.prev_node && counts > 0)
        counts -= 1
        last_node = curr_node
        curr_node = curr_node.prev_node
      end

      case [(last_node.x - c_pos[0]), (last_node.y - c_pos[1])]
      when [-1, 0] then 'l'
      when [1, 0] then 'r'
      when [0, -1] then 'u'
      when [0, 1] then 'd'
      end
    end

    def build_bitmap(target_pos, snakes)
      bm = []

      # obstacles will get an effective high distance to goal
      snakes.each do |x, y|
        bm[x + Engine::ARENA_WIDTH * y] = Node.new(x, y, OBSTACLE_VAL)
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

  class PriorityStack
    def initialize
      @prio_array = []
      @curr_prios = Set.new
    end

    def push(priority, node)
      @prio_array[priority] ||= []
      @curr_prios.add(priority)
      @prio_array[priority].push(node)
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
