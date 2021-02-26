val kamon            = "io.kamon"        %% "kamon-core"              % "2.1.8"
val kamonTestKit     = "io.kamon"        %% "kamon-testkit"           % "2.1.8"
val kamonLogback     = "io.kamon"        %% "kamon-logback"           % "2.1.8"
val kanela           = "io.kamon"         % "kanela-agent"            % "1.0.7"
val googleCloudCore  = "com.google.cloud" % "google-cloud-core"       % "1.93.3"
val googleMonitoring = "com.google.cloud" % "google-cloud-monitoring" % "1.99.2"
val googleTracing    = "com.google.cloud" % "google-cloud-trace"      % "1.0.3"
val sprayJson        = "io.spray"        %% "spray-json"              % "1.3.5"
val scalatest        = "org.scalatest"   %% "scalatest"               % "3.2.2"

val defaultScalaVersion = "2.13.3"

val mimaPreviousVersion = "1.2.0"

/** sbt-github-packages */
resolvers ++= Seq(Resolver.githubPackages("GlobalWebIndex"))
val publishSettings = Seq(
  githubOwner := "GlobalWebIndex",
  githubRepository := "kamon-stackdriver",
  githubTokenSource := TokenSource.Environment("GITHUB_TOKEN") || TokenSource.GitConfig("github.token")
)

githubTokenSource in Test := TokenSource.Environment("GITHUB_TOKEN") || TokenSource.GitConfig("github.token")

lazy val `kamon-stackdriver-root` = (project in file("."))
  .settings(noPublishing)
  .settings(publishSettings) // remove it when https://github.com/djspiewak/sbt-github-packages/pull/34 is fixed, ThisBuild/Global won't help
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq.empty,
    scalaVersion := defaultScalaVersion
  )
  .aggregate(`kamon-stackdriver`, `kamon-logback-stackdriver`)

val `kamon-stackdriver` = project
  .configs(IntegrationTest)
  .settings(publishSettings)
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings),
    libraryDependencies ++= providedScope(kanela) ++
      compileScope(kamon, googleMonitoring, googleTracing, googleCloudCore) ++
      ittestScope(logbackClassic, kamonTestKit, scalatest),
    crossScalaVersions := Seq("2.12.11", defaultScalaVersion),
    scalaVersion := defaultScalaVersion,
    mimaPreviousArtifacts := Set(organization.value %% moduleName.value % mimaPreviousVersion)
  )

def ittestScope(deps: sbt.ModuleID*): scala.Seq[sbt.ModuleID] = deps.map(_ % "it,test")

val `kamon-logback-stackdriver` = project
  .enablePlugins(JavaAgent)
  .settings(instrumentationSettings)
  .settings(publishSettings)
  .settings(
    libraryDependencies ++= providedScope(kanela) ++
      compileScope(kamon, kamonLogback, logbackClassic, googleCloudCore) ++
      testScope(sprayJson, kamonTestKit, scalatest),
    crossScalaVersions := Seq("2.12.11", defaultScalaVersion),
    scalaVersion := defaultScalaVersion,
    mimaPreviousArtifacts := Set(organization.value %% moduleName.value % mimaPreviousVersion)
  )
