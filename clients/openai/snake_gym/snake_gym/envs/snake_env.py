import gym
import numpy as np
from gym import error, spaces, utils
from gym.utils import seeding
from multiprocessing import Process, Queue
import queue
from .udp_client import UdpClient

SERVER_GRID_SIZE = 40 # Size defined by server
GRID_SIZE = SERVER_GRID_SIZE + 2 # Env grid size, includes walls on all four sides
APPLE_REWARD = 200
DEATH_PENALTY = -5

APPLE_GRID_VAL = 3
HEAD_GRID_VAL = 2
COLL_GRID_VAL = 1
class SnakeEnv(gym.Env):

    def __init__(self):
        self.curr_dir = None
        self.last_score = 0
        self.last_dist = None
        self.states = []
        self.snd_buffer = Queue()
        self.rcv_buffer = Queue()
        self.action_space = spaces.Discrete(5) # 0 do nothing, 1 ml; 2 mu; 3 mr; 4 md
        self.observation_space = spaces.Box(0, 3, (GRID_SIZE, GRID_SIZE, 1))
        self.reward_range = (-5, APPLE_REWARD + 1)
        self.p = Process(target=self.run_client, args=(self.snd_buffer, self.rcv_buffer,))
        self.p.start()
        
    def run_client(self, snd_buffer, rcv_buffer):
        uc = UdpClient(snd_buffer, rcv_buffer)
        uc.start()

    def step(self, action):
        if action == 0:
            cmd = None
        elif action == 1:
            cmd = 'ml'
        elif action == 2:
            cmd = 'mu'
        elif action == 3:
            cmd = 'mr'
        elif action == 4:
            cmd = 'md'
        else:
            cmd = None
        
        if cmd:
            self.snd_buffer.put(cmd)
        self.state = self.fetch_state()
        score  = self.state['score']
        done = self.state['snakeDead']
        appleX, appleY = self.state['appleCoords']
        headX, headY = self.state['headCoords']

        print(f"\rSend buffer size: {self.snd_buffer.qsize()}; Receive buffer size: {self.rcv_buffer.qsize()}", end="")

        dist = abs(appleX - headX) + abs(appleY - headY)
        if self.last_dist is None:
            self.last_dist = dist
        dist_change = self.last_dist - dist
        self.last_dist = dist
        
        reward = (score - self.last_score) * APPLE_REWARD + dist_change
        if done:
            reward -= abs(DEATH_PENALTY)
        self.last_score = score

        observation = self.normalize_state(self.state)
        return observation, reward, done, {}

    def fetch_state(self):
        state = self.rcv_buffer.get()
        _payload_size, payload = state.decode("ASCII").rstrip("\n").split("::")
        pInfo, *states = payload.split("#")
        pIndex = int(pInfo.split("_")[0])
        appleX, appleY = states[0].split(",")
        pState = states[pIndex + 1]
        snakeState, score, snake = pState.split("|")
        head, self.curr_dir, tail = snake.split("_")
        headX, headY = head.split(",")
        return {
            'score': int(score),
            'snakeDead': snakeState == 'd',
            'appleCoords': (int(appleX), int(appleY)),
            'headCoords': (int(headX), int(headY)),
            'collisionCoords': self.tail_coords(head_coords=(int(headX), int(headY)), tail_string=tail)
        }

    def normalize_state(self, state):
        out = np.zeros((GRID_SIZE, GRID_SIZE), dtype=np.uint8)
        # Add wall values
        out[0,:] = COLL_GRID_VAL
        out[GRID_SIZE - 1,:] = COLL_GRID_VAL
        out[:,0] = COLL_GRID_VAL
        out[:, GRID_SIZE - 1] = COLL_GRID_VAL

        appleX, appleY = state['appleCoords']
        out[appleY + 1, appleX + 1] = APPLE_GRID_VAL

        headX, headY = state['headCoords']
        if headX < GRID_SIZE and headY < GRID_SIZE and headX >= 0 and headY >= 0:
            out[headY + 1, headX + 1] = HEAD_GRID_VAL

        for cx, cy in state['collisionCoords']:
            out[int(cy) + 1, int(cx) + 1] = COLL_GRID_VAL
        return np.reshape(out, (GRID_SIZE, GRID_SIZE, 1))

    def tail_coords(self, head_coords, tail_string):
        out = []
        curr = head_coords
        for t in tail_string:
            if t == 'r': curr = (curr[0] + 1, curr[1])
            elif t == 'l': curr = (curr[0] - 1, curr[1])
            elif t == 'u': curr = (curr[0], curr[1] - 1)
            else: curr = (curr[0], curr[1] + 1)
            
            out.append(curr)
        return out


    def reset(self):
        print("")
        self.clear_buffers()

        self.curr_dir = None
        self.last_score = 0
        self.last_dist = None
        self.states = []

        # Send restart signal
        self.snd_buffer.put('s')
        self.snd_buffer.put('r')

        self.state = self.fetch_state()
        return self.normalize_state(self.state)

    def render(self, mode='human'):
        norm_state = np.array(self.normalize_state(self.state))
        size_y, size_x, *_rem_shape = norm_state.shape
        for y in range(size_y):
            for x in range(size_x):
                cell_val = norm_state[y, x]
                if cell_val == APPLE_GRID_VAL:
                    print('o', end='')
                elif cell_val == HEAD_GRID_VAL:
                    print('H', end='')
                elif cell_val == COLL_GRID_VAL:
                    print('#', end='')
                else:
                    print(' ', end='')
            print('')


    def close(self):
        self.snd_buffer.put('q')
        self.p.join(2000)

    def clear_buffers(self):
        while not self.snd_buffer.empty():
            try:
                self.snd_buffer.get_nowait()
            except queue.Empty:
                pass
        while not self.rcv_buffer.empty():
            try:
                self.rcv_buffer.get_nowait()
            except queue.Empty:
                pass