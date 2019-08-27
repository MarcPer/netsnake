name := "netsnake"

scalaVersion := "2.11.12"
version := "1.0"

lazy val akkaVersion = "2.5.24"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)
