import gym
import signal
import sys

class Game:
    stop = False

    def __init__(self):
        self.env = gym.make('snake_gym:Netsnake-v0')
        self.stop = False
        # signal.signal(signal.SIGINT, self.cleanup)
        # signal.signal(signal.SIGTERM, self.cleanup)
        signal.signal(signal.SIGALRM, self.force_quit)

    def cleanup(self, _signo, _frame):
        self.env.close()
        signal.alarm(5)

    def force_quit(self, _signo, _frame):
        sys.exit()

    def run(self):
        for _i_episode in range(10):
            if self.stop: break
            _observation = self.env.reset()
            for t in range(2000):
                if self.stop: break
                action = self.env.action_space.sample()
                observation, reward, done, info = self.env.step(action)
                self.env.render()
                if done:
                    print("Episode finished after {} timesteps".format(t+1))
                    break
        self.env.close()

if __name__ == '__main__':
    game = Game()
    game.run()
