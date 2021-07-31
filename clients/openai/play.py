import gym

from stable_baselines.common.vec_env import DummyVecEnv
from stable_baselines import DQN

env = gym.make('snake_gym:Netsnake-v0')
model = DQN.load("deepq_netsnake")

obs = env.reset()
for i_episode in range(20):
    obs = env.reset()
    for t in range(20000):
        action, _states = model.predict(obs)      
        obs, rewards, done, info = env.step(action)
        env.render()
        if done:
            print(f"Episode {i_episode} finished after {t+1} timesteps")
            break
env.close()
