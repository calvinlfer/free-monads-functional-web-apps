name := "free-monad-experiment"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val http4s = "org.http4s"
  val https4sV = "0.14.11a"
  Seq(
    http4s            %% "http4s-core"          % https4sV,
    http4s            %% "http4s-dsl"           % https4sV,
    http4s            %% "http4s-blaze-server"  % https4sV,
    http4s            %% "http4s-circe"         % https4sV,
    "io.circe"        %% "circe-generic"        % "0.4.1",
    "org.scalaz"      %% "scalaz-core"          % "7.2.7",
    "org.slf4j"       % "slf4j-api"             % "1.7.21",
    "ch.qos.logback"  % "logback-classic"       % "1.1.7"
  )
}