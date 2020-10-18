import Dependencies._

ThisBuild / scalaVersion := "2.13.3"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "net.ardfard"
ThisBuild / organizationName := "ardfard"

val zioVersion = "1.0.3"
val circeVersion = "0.12.3"
val sttpVersion = "2.2.9"

lazy val root = (project in file("."))
  .settings(
    name := "mergetrain",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-test" % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test",
      "dev.zio" %% "zio-macros" % zioVersion,
      "com.softwaremill.sttp.client" %% "core" % sttpVersion,
      "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % sttpVersion,
      "com.softwaremill.sttp.client" %% "circe" % sttpVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "net.debasishg" %% "redisclient" % "3.30"
    ),
    scalacOptions += "-Ymacro-annotations",
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
