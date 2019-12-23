val kamon               = "io.kamon" %% "kamon-core" % "2.0.4"
val kamonTestKit        = "io.kamon" %% "kamon-testkit" % "2.0.4"
val kamonLogback        = "io.kamon" %% "kamon-logback" % "2.0.2"
val kanela              = "io.kamon" % "kanela-agent" % "1.0.3"
val googleCloudCore     = "com.google.cloud" % "google-cloud-core" % "1.92.0"
val googleMonitoring    = "com.google.cloud" % "google-cloud-monitoring" % "1.99.0"
val googleTracing       = "com.google.cloud" % "google-cloud-trace" % "0.108.0-beta"
val sprayJson           = "io.spray" %% "spray-json" % "1.3.5"
val defaultScalaVersion = "2.13.1"

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
      compileScope(kamon, googleMonitoring, googleTracing) ++
      ittestScope(logbackClassic, kamonTestKit, scalatest),
    bintrayOrganization := Some("gwidx"),
    bintrayRepository := "maven",
    bintrayVcsUrl := Some("https://github.com/GlobalWebIndex/kamon-stackdriver.git"),
    crossScalaVersions := Seq("2.12.10", "2.13.1"),
    scalaVersion := defaultScalaVersion
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
    crossScalaVersions := Seq("2.12.10", "2.13.1"),
    scalaVersion := defaultScalaVersion
  )
