import sbt._

object Dependencies {
  val kamon            = "io.kamon"         %% "kamon-core"             % "1.1.6"
  val kamonTestKit     = "io.kamon"         %% "kamon-testkit"          % "1.1.1"
  val scalatest        = "org.scalatest"    %% "scalatest"              % "3.0.8"
  val logback          = "ch.qos.logback"   % "logback-classic"         % "1.2.3"
  val googleMonitoring = "com.google.cloud" % "google-cloud-monitoring" % "1.83.0"
  val googleTracing    = "com.google.cloud" % "google-cloud-trace"      % "0.101.0-beta"
}
