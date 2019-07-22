val kamon            = "io.kamon"         %% "kamon-core"             % "1.1.6"
val kamonTestKit     = "io.kamon"         %% "kamon-testkit"          % "1.1.6"
val kamonLogback     = "io.kamon"         %% "kamon-logback"          % "1.0.7"
val googleCloudCore  = "com.google.cloud" % "google-cloud-core"       % "1.83.0"
val googleMonitoring = "com.google.cloud" % "google-cloud-monitoring" % "1.83.0"
val googleTracing    = "com.google.cloud" % "google-cloud-trace"      % "0.101.0-beta"
val sprayJson        = "io.spray"         %% "spray-json"             % "1.3.5"

ThisBuild / crossScalaVersions := Seq("2.11.12", "2.12.8")

lazy val `kamon-stackdriver` = project
  .settings(
    libraryDependencies ++=
      compileScope(kamon, googleMonitoring, googleTracing) ++ testScope(logbackClassic, kamonTestKit, scalatest)
  )

lazy val `kamon-logback-stackdriver` = project
  .settings(
    libraryDependencies ++= compileScope(kamon, kamonLogback, logbackClassic, googleCloudCore) ++ testScope(sprayJson, kamonTestKit, scalatest)
  )
