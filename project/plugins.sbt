lazy val root: Project     = project.in(file(".")).dependsOn(latestSbtUmbrella)
lazy val latestSbtUmbrella = ProjectRef(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-2.x"), "kamon-sbt-umbrella")

addSbtPlugin("org.scalameta"             % "sbt-scalafmt"    % "2.4.2")
addSbtPlugin("com.dwijnand"              % "sbt-dynver"      % "4.1.1")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"    % "0.1.19")
addSbtPlugin("com.typesafe"              % "sbt-mima-plugin" % "0.8.1")
