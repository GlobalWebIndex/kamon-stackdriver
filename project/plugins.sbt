lazy val root: Project     = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = ProjectRef(uri("git://github.com/lustefaniak/kamon-sbt-umbrella.git#kamon-2.x"), "kamon-sbt-umbrella")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.6")
addSbtPlugin("com.dwijnand"  % "sbt-dynver"   % "4.0.0")
