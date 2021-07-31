import os

import gym

from stable_baselines import ACER
from stable_baselines.common.policies import MlpPolicy, MlpLstmPolicy, MlpLnLstmPolicy
from stable_baselines.common import make_vec_env
from stable_baselines.common.vec_env import DummyVecEnv


# Create and wrap the environment
# env = make_vec_env('snake_gym:Netsnake-v0', n_envs=4)
env = gym.make('snake_gym:Netsnake-v0')
env = DummyVecEnv([lambda: env])

# Start Tensorboard local web server during training to see the agent progress, with:
# tensorboard --logdir acer_netsnake_tensorboard/
model = ACER(MlpPolicy, env, verbose=1, tensorboard_log="./acer_netsnake_tensorboard/")
# model = ACER.load(f"netsnake_acer0", verbose=1, tensorboard_log="./acer_netsnake_tensorboard/")
# model.set_env(env)
time_steps = 5_000

model.learn(total_timesteps=time_steps)
model.save(f"netsnake_acer0")

for tsession in range(100):
  print(f"--- Beginning training session {tsession + 1} ---")
  model = ACER.load(f"netsnake_acer{tsession}", verbose=1, tensorboard_log="./acer_netsnake_tensorboard/")
  model.set_env(env)
  model.learn(total_timesteps=time_steps)
  model.save(f"netsnake_acer{tsession + 1}")
