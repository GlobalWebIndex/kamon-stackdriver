val kamon            = "io.kamon"            %% "kamon-core"               % "2.1.1"
val kamonTestKit     = "io.kamon"            %% "kamon-testkit"            % "2.1.1"
val kamonLogback     = "io.kamon"            %% "kamon-logback"            % "2.1.1"
val kanela           = "io.kamon"             % "kanela-agent"             % "1.0.5"
val googleCloudCore  = "com.google.cloud"     % "google-cloud-core"        % "1.93.5"
val googleMonitoring = "com.google.cloud"     % "google-cloud-monitoring"  % "1.100.1"
val googleTracing    = "com.google.cloud"     % "google-cloud-trace"       % "1.0.3"
val sprayJson        = "io.spray"            %% "spray-json"               % "1.3.5"
val scalatest        = "org.scalatest"       %% "scalatest"                % "3.1.2"
val logstashEncoder  = "net.logstash.logback" % "logstash-logback-encoder" % "6.4"

val defaultScalaVersion = "2.13.3"

lazy val `kamon-stackdriver-root` = (project in file("."))
  .settings(noPublishing)
  .settings(
    skip in publish := true,
    bintrayEnsureBintrayPackageExists := {},
    crossScalaVersions := Seq.empty,
    scalaVersion := defaultScalaVersion
  )
  .aggregate(`kamon-stackdriver`, `kamon-logback-stackdriver`)

val `kamon-stackdriver` = project
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings),
    libraryDependencies ++= providedScope(kanela) ++
      compileScope(kamon, googleMonitoring, googleTracing, googleCloudCore) ++
      ittestScope(logbackClassic, kamonTestKit, scalatest),
    bintrayOrganization := Some("gwidx"),
    bintrayRepository := "maven",
    bintrayVcsUrl := Some("https://github.com/GlobalWebIndex/kamon-stackdriver.git"),
    crossScalaVersions := Seq("2.12.11", defaultScalaVersion),
    scalaVersion := defaultScalaVersion
  )

def ittestScope(deps: sbt.ModuleID*): scala.Seq[sbt.ModuleID] = deps.map(_ % "it,test")

val `kamon-logback-stackdriver` = project
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(
    libraryDependencies ++= providedScope(kanela) ++
      compileScope(kamon, kamonLogback, logbackClassic, googleCloudCore) ++
      testScope(sprayJson, kamonTestKit, scalatest, logstashEncoder),
    bintrayOrganization := Some("gwidx"),
    bintrayRepository := "maven",
    bintrayVcsUrl := Some("https://github.com/GlobalWebIndex/kamon-stackdriver.git"),
    crossScalaVersions := Seq("2.12.11", defaultScalaVersion),
    scalaVersion := defaultScalaVersion
  )
