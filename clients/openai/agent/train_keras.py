import gym
import numpy as np
import time
from snake_agent import SnakeAgent, MODEL_NAME, UPDATE_TARGET_EVERY

env = gym.make('snake_gym:Netsnake-v0')

agent = SnakeAgent(env)

NUM_EPISODES = 1_000
MIN_EPSILON = 0.01
MAX_EPSILON = 1
EPSILON_DECAY_RATE = 0.01 # epsilon is changed on each episode
AGGREGATE_STATS_EVERY = UPDATE_TARGET_EVERY
MAX_TIMESTEPS = 20_000

MIN_REWARD = 7

ep_rewards = []
reward_avg = 0
reward_min = 0
reward_max = 0

current_max_avg_reward = -np.Inf
epsilon = MAX_EPSILON
for episode in range(1, NUM_EPISODES + 1):
  print(f"-- Episode {episode} starting\nepsilon = {epsilon}; max avg reward = {current_max_avg_reward}")
  done = False
  episode_reward = 0
  state = env.reset()
  for t in range(MAX_TIMESTEPS):
    if done: break
    epsilon_test = np.random.uniform(low=0.0, high=1.0)
    if epsilon_test <= epsilon:
      action = env.action_space.sample()
    else:
      action = np.argmax(agent.get_qs(state))
    
    new_state, reward, done, info = env.step(action)
    episode_reward += reward
    # print(f"\rEpisode reward: {episode_reward}", end='')
    agent.append_memory((state, action, reward, new_state, done))
    agent.train(done)
    state = new_state
  ep_rewards.append(episode_reward)
  if (episode % AGGREGATE_STATS_EVERY == 0) or episode == 1:
    reward_avg = sum(ep_rewards[-AGGREGATE_STATS_EVERY:])/len(ep_rewards[-AGGREGATE_STATS_EVERY:])
    reward_min = min(ep_rewards[-AGGREGATE_STATS_EVERY:])
    reward_max = max(ep_rewards[-AGGREGATE_STATS_EVERY:])
    agent.tensorboard.update_stats(reward_avg=reward_avg, reward_min=reward_min, reward_max=reward_max, epsilon=epsilon, episode=episode)
    if reward_avg > current_max_avg_reward:
      current_max_avg_reward = reward_avg
    # env.render()
    print('')

  epsilon = MIN_EPSILON + (MAX_EPSILON - MIN_EPSILON)*np.exp(-EPSILON_DECAY_RATE * episode)
  # Save model, but only when min reward is greater or equal a set value
  if reward_avg >= MIN_REWARD:
      agent.model.save(f'models/{MODEL_NAME}__{reward_max:_>7.2f}max_{reward_avg:_>7.2f}avg_{reward_min:_>7.2f}min__{int(time.time())}.model')

env.close()
