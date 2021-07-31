import select
import socket

class UdpClient:
  UDP_IP = '127.0.0.1'
  UDP_PORT = 3000
  READ_ONLY = select.POLLIN | select.POLLPRI | select.POLLHUP | select.POLLERR
  READ_WRITE = READ_ONLY | select.POLLOUT
  TIMEOUT = 1000

  def __init__(self, cmd_buffer, state_buffer):
      self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
      self.sock.settimeout(1000)
      self.sock.connect((self.UDP_IP, self.UDP_PORT))
      self.cmd_buffer = cmd_buffer
      self.state_buffer = state_buffer
      self.last_data = None

  def start(self):
      self.sock.send(b's')
      done = False
      while True:
          if done: break
          readable, _, _ = select.select([self.sock, self.cmd_buffer._reader], [], [])
          for s in readable:
              if s is self.cmd_buffer._reader:
                  cmd = self.cmd_buffer.get()
                  if cmd == "q": done = True
                  self.sock.send(cmd.encode())
              elif s is self.sock:
                  try:
                    data, _ = s.recvfrom(1024) # buffer size is 1024 bytes
                    if self.state_buffer.empty() and data != self.last_data:
                      self.state_buffer.put(data)
                      self.last_data = data
                  except:
                    done = True

