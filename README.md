# Netsnake

Server and client implementations for a multiplayer Snake game over UDP.

## Getting started

**Start server**
Run the server with
```
cd server
sbt run
```

**Simple client**
Use netcat as a rudimentary client:
```
nc -u localhost 3000
```

Then send commands from standard input. Start by sending `s` (`s + <Enter>`), then start sending either of `mu` (move up), `md` (move down), `mr` (move right), `ml` (move left). See full protocol below.

The server sends, in fixed intervals, the current position of the snake, the apple, and the current score. Netcat prints it out.

## Protocol

The server listens at port 3000, and undestands the following commands:

Command | Description
--------|------------
s       | Start game.
mu      | Move snake up.
md      | Move snake down.
mr      | Move snake right.
ml      | Move snake left.

On every game cycle, the server broadcasts the current game state to all connected players, in the following format:

```
a<snake alive?>|<score>|<appleX, appleY>|<snakeX, snakeY>_<snake direction>_<tail relative positions...>
```

Here is an example:
```
27,17|a1|100|18,20_L_rruul
|     |  |   |     | |
|     |  |   |     | - Starting from its head, the snake tail goes twice right, twice up, and once to the left (see drawing below)
|     |  |   |     - Snake moving towards left (L).
|     |  |   - Snake head in positiion (18,20)
|     |  - Score 100
|     - Snake is alive (1)
- Apple in position (27, 17)
```

The allowed values for each parameter is as follows:

Parameter                   | Possible values
----------------------------|----------------
`<snake alive?>`            | `0` (dead) or `1` (alive)
`<score>`                   | integer
`<appleX, appleY>`          | two comma-separated integers. `x` ranges from `0` to screen width, and `y` ranges from `0` to screen height.
`<snakeX, snakeY>`          | two comma-separated integers. `x` ranges from `0` to screen width, and `y` ranges from `0` to screen height.
`<snake direction>`         | `L` (Left), `R` (Right), `U` (Up), `D` (Down). Values are always in upper case.
`<tail relative positions>` | sequence of 0 or more `l`, `r`, `u`, and `d`. Values are always in lower case.

The tail relative positions indicate in sequence, the position of the next part of the snake's tail, starting from the head. For example, the `rruul` above would be drawn as follows:

```
 xx
  x
oxx
```
where `o` indicates the snake head. The sequence `rruul` indicates the first portion of the tail is to the right of the head, the second portion is to the right of the first portion, followed by upwards twice, followed by left.

The game is over and broadcast stops when all snakes are dead.
