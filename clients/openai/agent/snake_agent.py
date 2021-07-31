import random
import time
import numpy as np
from collections import deque
from tensorflow.keras import models, layers
from modified_tensor_board import ModifiedTensorBoard

DISCOUNT = 0.3
MODEL_NAME = 'snake_dqn'

REPLAY_MEMORY_SIZE = 100_000
MIN_REPLAY_MEMORY_SIZE = 200
MINIBATCH_SIZE = 40
UPDATE_TARGET_EVERY = 5

class SnakeAgent:
  def __init__(self, env):
    self.env = env
    self.model = self.create_model()
    self.target_model = self.create_model()
    self.replay_memory = deque(maxlen=REPLAY_MEMORY_SIZE)
    self.tensorboard = ModifiedTensorBoard(log_dir=f"logs/{MODEL_NAME}-{int(time.time())}")
    self.target_update_counter = 0

  def create_model(self):
    model = models.Sequential()
    model.add(layers.Conv2D(filters=16, kernel_size=(4,4), strides=(2,2), activation='relu', padding="valid", input_shape=self.env.observation_space.shape))
    model.add(layers.Conv2D(filters=32, kernel_size=(2,2), strides=(1,1), activation='relu', padding="valid"))
    model.add(layers.Flatten())
    model.add(layers.Dense(256, activation="relu"))
    model.add(layers.Dense(self.env.action_space.n))

    model.compile(loss='mse', optimizer='adam', metrics=['accuracy'])
    return model

  def append_memory(self, transition):
    self.replay_memory.append(transition)
  
  def train(self, terminal_state):
    if len(self.replay_memory) < MIN_REPLAY_MEMORY_SIZE:
      return

    minibatch = random.sample(self.replay_memory, MINIBATCH_SIZE)

    # Get current states from minibatch, then query NN model for Q values
    current_states = np.array([transition[0] for transition in minibatch])
    current_qs_list = self.model.predict(current_states)

    # Get future states from minibatch, then query NN model for Q values
    # When using target network, query it, otherwise main network should be queried
    new_current_states = np.array([transition[3] for transition in minibatch])
    future_qs_list = self.target_model.predict(new_current_states)

    X = []
    y = []

    # Now we need to enumerate our batches
    for index, (current_state, action, reward, _new_current_state, done) in enumerate(minibatch):

      # If not a terminal state, get new q from future states, otherwise set it to 0
      # almost like with Q Learning, but we use just part of equation here
      if not done:
        max_future_q = np.max(future_qs_list[index])
        new_q = reward + DISCOUNT * max_future_q
      else:
        new_q = reward

      # Update Q value for given state
      current_qs = current_qs_list[index]
      current_qs[action] = new_q

      # And append to our training data
      X.append(current_state)
      y.append(current_qs)

    # Fit on all samples as one batch, log only on terminal state
    self.model.fit(np.array(X), np.array(y), batch_size=MINIBATCH_SIZE, verbose=0, shuffle=False, callbacks=[self.tensorboard] if terminal_state else None)

    # Update target network counter every episode
    if terminal_state:
      self.target_update_counter += 1

    # If counter reaches set value, update target network with weights of main network
    if self.target_update_counter > UPDATE_TARGET_EVERY:
      self.target_model.set_weights(self.model.get_weights())
      self.target_update_counter = 0

  def get_qs(self, state):
    return self.model.predict(np.reshape(state, (1, *self.env.observation_space.shape)))
