import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.{Cmd, DockerPermissionStrategy}


enablePlugins(DockerPlugin, RpmPlugin, AshScriptPlugin)

name := "matrixstore-client"

version := "0.1"

scalaVersion := "2.13.6"

val circeVersion = "0.14.0"
val slf4jVersion = "1.7.25"
val akkaVersion = "2.6.15"

lazy val `common` = (project in file("common"))
  .settings(
    Docker / aggregate := false,
    Docker / publish := {},
    scalaVersion := "2.13.6",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion %Test,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-yaml" % "0.12.0",
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "commons-codec" % "commons-codec" % "1.12",
      "commons-io" % "commons-io" % "2.6",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.github.scopt" %% "scopt" % "3.7.1",
      "org.specs2" %% "specs2-core" % "4.5.1" % Test,
      "org.specs2" %% "specs2-mock" % "4.5.1" % Test,
      "org.mockito" % "mockito-core" % "2.28.2" % Test
    ),
    updateOptions := updateOptions.value.withCachedResolution(false),
      Compile / unmanagedJars  += file("lib/mxsjapi.jar"),
    Compile / unmanagedJars += file("lib/oncrpc.jar"),
  )

lazy val root = (project in file("."))
  .dependsOn(`common`)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
      // https://mvnrepository.com/artifact/org.jline/jline
      "org.jline" % "jline" % "3.20.0"
    ),
    scalaVersion := "2.13.6",
    version := sys.props.getOrElse("build.number","DEV"),
    dockerPermissionStrategy := DockerPermissionStrategy.CopyChown,
    Docker / daemonUserUid := None,
    Docker / daemonUser  := "daemon",
    dockerUsername  := sys.props.get("docker.username"),
    Docker / packageName  := "guardianmultimedia/matrixstore-client",
    packageName := "manual-media-backup",
    dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"matrixstore-client",Some(sys.props.getOrElse("build.number","DEV"))),
    dockerBaseImage := "openjdk:8-jdk-slim",
  )