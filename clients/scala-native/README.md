# Netsnake Scala Native

This code is a shameless adaptation of [the following gist](https://gist.github.com/densh/1885e8b03127fd52ff659505d8b3b76b), by Denis Shabalin. It uses Scala native and SDL2.

## Getting started

Install [Scala sbt](https://www.scala-sbt.org/) and dependencies for [Scala native](https://github.com/scala-native/scala-native). In Ubuntu, the latter is done with
```
apt-get install clang libgc-dev libunwind-dev libsdl2-dev
```

Go into the root directory and run `sbt run`.
