name := "clashcode"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  "com.typesafe.akka" % "akka-remote_2.10" % "2.2.0"
)     

play.Project.playScalaSettings
