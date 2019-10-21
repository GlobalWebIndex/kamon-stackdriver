val kamon            = "io.kamon"         %% "kamon-core"             % "2.0.1"
val kamonTestKit     = "io.kamon"         %% "kamon-testkit"          % "2.0.1"
val kamonLogback     = "io.kamon"         %% "kamon-logback"          % "2.0.1"
val kanela           = "io.kamon"         % "kanela-agent"            % "1.0.1"
val googleCloudCore  = "com.google.cloud" % "google-cloud-core"       % "1.91.2"
val googleMonitoring = "com.google.cloud" % "google-cloud-monitoring" % "1.98.0"
val googleTracing    = "com.google.cloud" % "google-cloud-trace"      % "0.108.0-beta"
val sprayJson        = "io.spray"         %% "spray-json"             % "1.3.5"

lazy val `kamon-stackdriver-root` = (project in file("."))
  .settings(noPublishing)
  .settings(
    skip in publish := true,
    bintrayEnsureBintrayPackageExists := {},
    crossScalaVersions := Seq.empty
  )
  .aggregate(`kamon-stackdriver`, `kamon-logback-stackdriver`)

val `kamon-stackdriver` = project
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    libraryDependencies ++= providedScope(kanela) ++
      compileScope(kamon, googleMonitoring, googleTracing) ++
      ittestScope(logbackClassic, kamonTestKit, scalatest),
    bintrayOrganization := Some("gwidx"),
    bintrayRepository := "maven",
    bintrayVcsUrl := Some("https://github.com/GlobalWebIndex/kamon-stackdriver.git"),
    scalaVersion := "2.12.10",
    crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1")
  )

def ittestScope(deps: sbt.ModuleID*): scala.Seq[sbt.ModuleID] = deps.map(_ % "it,test")

val `kamon-logback-stackdriver` = project
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= providedScope(kanela) ++
      compileScope(kamon, kamonLogback, logbackClassic, googleCloudCore) ++
      testScope(sprayJson, kamonTestKit, scalatest),
    bintrayOrganization := Some("gwidx"),
    bintrayRepository := "maven",
    bintrayVcsUrl := Some("https://github.com/GlobalWebIndex/kamon-stackdriver.git"),
    scalaVersion := "2.12.10",
    crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1")
  )
