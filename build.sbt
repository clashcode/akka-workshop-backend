name := "clashcode"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  "com.typesafe.akka" %% "akka-cluster"    % "2.2.3",
  "com.typesafe.akka" %% "akka-slf4j"      % "2.2.3",
  "com.typesafe.akka" %% "akka-testkit"    % "2.2.3"
)

play.Project.playScalaSettings
