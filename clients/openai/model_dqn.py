import gym

from stable_baselines.common.vec_env import DummyVecEnv
from stable_baselines.deepq.policies import MlpPolicy
from stable_baselines import DQN

env = gym.make('snake_gym:Netsnake-v0')

print("--- Beginning training session 0 ---")
model = DQN(MlpPolicy, env, verbose=1, gamma=0.3)
model.learn(total_timesteps=25000)
model.save("deepq_netsnake0")
for tsession in range(100):
  print(f"--- Beginning training session {tsession + 1} ---")
  model = DQN.load(f"deepq_netsnake{tsession}")
  model.set_env(env)
  model.learn(total_timesteps=25000)
  model.save(f"deepq_netsnake{tsession + 1}")

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
