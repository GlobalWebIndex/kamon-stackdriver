import Dependencies._
import scalariform.formatter.preferences._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "nl.markvandertol",
      scalaVersion := "2.12.4",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "Kamon-Stackdriver",
    crossScalaVersions := Seq("2.12.4", "2.11.12", "2.10.7"),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"),
    libraryDependencies ++= List(
      specs2 % Test,
      kamon,
      googleMonitoring,
      googleTracing,
      logback),
    scalariformPreferences := scalariformPreferences.value
  )
