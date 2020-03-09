# frozen_string_literal: true

class UdpClient
  attr_reader :server_addr, :server_port
  def initialize(server_queue, input_queue, server_addr, server_port)
    @server_queue = server_queue
    @input_queue = input_queue
    @server_addr = server_addr
    @server_port = server_port
    @socket = UDPSocket.new
    source_port = 1300 + rand(500)
    @socket.bind('127.0.0.1', source_port)
    puts "Bound to 127.0.0.1:#{source_port}"
    @socket.send('s', 0, server_addr, server_port)
    @stop = false
  end

  def start
    loop do
      break if @stop
      io = IO.select([@socket, @input_queue])
      handle_io(io ? io.first : [])
    end
  end

  def handle_io(readers)
    readers.each do |reader|
      if reader == @input_queue
        command = @input_queue.pop
        @socket.send(command, 0, server_addr, server_port)
        @stop = command == 'q'
      elsif reader == @socket
        begin
          data = @socket.recvfrom_nonblock(300)&.[](0)
          @server_queue << data
        rescue IO::WaitReadable
          IO.select([@socket], [])
          retry
        end
      end
    end
  end
end