import gym
from stable_baselines.common.env_checker import check_env

env = gym.make('snake_gym:Netsnake-v0')
check_env(env)
env.close()
