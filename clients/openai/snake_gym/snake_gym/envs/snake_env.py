import gym
from gym import error, spaces, utils
from gym.utils import seeding
from multiprocessing import Process, Queue
from .udp_client import UdpClient

class SnakeEnv(gym.Env):
    def __init__(self):
        self.curr_dir = 'd'
        self.states = []
        self.snd_buffer = Queue()
        self.rcv_buffer = Queue()
        self.action_space = spaces.Discrete(3) # 0 do nothing, 1 turn left; 2 turn right
        self.p = Process(target=self.run_client, args=(self.snd_buffer, self.rcv_buffer,))
        self.p.start()
        
    def run_client(self, snd_buffer, rcv_buffer):
        uc = UdpClient(snd_buffer, rcv_buffer)
        uc.start()

    def step(self, action):
        if action == 0:
            cmd = None
        elif self.curr_dir == 'U':
            cmd = 'mr' if action == 2 else 'ml'
        elif self.curr_dir == 'D':
            cmd = 'ml' if action == 2 else 'mr'
        elif self.curr_dir == 'R':
            cmd = 'md' if action == 2 else 'mu'
        elif self.curr_dir == 'L':
            cmd = 'mu' if action == 2 else 'md'
        else:
            cmd = None
        
        if cmd: self.snd_buffer.put(cmd)
        state = self.rcv_buffer.get()
        _payload_size, payload = state.decode("ASCII").rstrip("\n").split("::")
        pInfo, *self.states = payload.split("#")
        pIndex = int(pInfo.split("_")[0])
        appleX, appleY = self.states[0].split(",")
        pState = self.states[pIndex + 1]
        snakeState, score, snake = pState.split("|")
        head, self.curr_dir, _tail = snake.split("_")
        headX, headY = head.split(",")
        reward = int(score) * 100 - abs(int(appleX) - int(headX)) - abs(int(appleY) - int(headY))
        done = snakeState == "d"
        return self.states, reward, done, {}

    def reset(self):
        self.snd_buffer.put('s')
        self.snd_buffer.put('r')
        return ''

    def render(self, mode='human'):
        print(self.states)

    def close(self):
        self.snd_buffer.put('q')
        self.p.join(2000)
