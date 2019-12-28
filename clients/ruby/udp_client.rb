# frozen_string_literal: true

class UdpClient
  def initialize(server_queue, input_queue, server_addr, server_port)
    @server_queue = server_queue
    @input_queue = input_queue
    @socket = UDPSocket.new
    @socket.connect(server_addr, server_port)
    @socket.send('s', 0)
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
        @socket.send(command, 0)
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