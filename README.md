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

The server sends, in fixed intervals, the current position all the snakes, the apple, and the current score. Netcat prints it out.

To connect from multiple local `nc` clients, specify different ports with the `-p` parameter.

## Protocol

The server listens at port 3000, and undestands the following commands:

Command | Description
--------|------------
s       | Start game.
j       | Join match.
r       | Restart (only if dead).
q       | Quit match.
mu      | Move snake up.
md      | Move snake down.
mr      | Move snake right.
ml      | Move snake left.

On every game cycle, the server broadcasts the current game state to all connected players, in the following format:

```
<player_index>_<num_players>$<appleX, appleY>$[<player_state>...]
```

There is one `<player_state>` per player in the match (format described further down), each separated by a `$` from each other. `<player_index>` is the index for the current player in the array of _player states_. Note that this is the only value that is different for each connected client.

`<num_players>` is the current number of connected players.
`<appleX, appleY>` are the apple's coordinates, separated by a comma.

The `<player_state>` has the following format:
```
<snake_state>|<score>|<snakeX, snakeY>_<snake_direction>_<tail_relative_positions>
```

The `<snake_state>` is one of the following:

State | Description
------|------------
d     | Dead.
r     | Running.
w     | Waiting (joined match, but no start command has been issued).

`<score>` is an integer, `<snakeX, snakeY>` are the coordinates of the snake's head, separated by a comma. `<snake_direction>` is one of `U` (up), `D` (down), `R` (right), `L` (left).

The tail relative positions indicate in sequence, the position of the next part of the snake's tail, starting from the head. For example, the `rruul` above would be drawn as follows:

```
 xx
  x
oxx
```
where `o` indicates the snake head. The sequence `rruul` indicates the first portion of the tail is to the right of the head, the second portion is to the right of the first portion, followed by upwards twice, followed by left.

The allowed values for each parameter is as follows:

Parameter                   | Possible values
----------------------------|----------------
`<snake state>`             | `d` (dead), `r` (running), `w` (waiting)
`<score>`                   | integer
`<appleX, appleY>`          | two comma-separated integers. `x` ranges from `0` to screen width, and `y` ranges from `0` to screen height.
`<snakeX, snakeY>`          | two comma-separated integers. `x` ranges from `0` to screen width, and `y` ranges from `0` to screen height.
`<snake direction>`         | `L` (Left), `R` (Right), `U` (Up), `D` (Down). Values are always in upper case.
`<tail relative positions>` | sequence of 0 or more `l`, `r`, `u`, and `d`. Values are always in lower case.

> Note that "Up" means decreasing Y coordinate.

The game is over and broadcast stops when all snakes have disconnected.
