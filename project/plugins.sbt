lazy val root: Project     = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = ProjectRef(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-1.x"), "kamon-sbt-umbrella")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.0")
addSbtPlugin("com.dwijnand"  % "sbt-dynver"   % "4.0.0")
