val kamon            = "io.kamon"         %% "kamon-core"             % "1.1.6"
val kamonTestKit     = "io.kamon"         %% "kamon-testkit"          % "1.1.1"
val googleMonitoring = "com.google.cloud" % "google-cloud-monitoring" % "1.83.0"
val googleTracing    = "com.google.cloud" % "google-cloud-trace"      % "0.101.0-beta"

lazy val root = (project in file("."))
  .settings(
    name := "kamon-stackdriver",
    libraryDependencies ++=
      compileScope(kamon, googleMonitoring, googleTracing) ++ testScope(logbackClassic, kamonTestKit, scalatest)
  )
