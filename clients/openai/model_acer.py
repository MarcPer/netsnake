import gym

from stable_baselines.common.policies import MlpPolicy, MlpLstmPolicy, MlpLnLstmPolicy
from stable_baselines import ACER

env = gym.make('snake_gym:Netsnake-v0')

model = ACER(MlpPolicy, env)
model.learn(total_timesteps=25000)
model.save("netsnake_acer0")

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
