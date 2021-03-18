ThisBuild / publishTo := Some("GitHub Package Registry" at "https://maven.pkg.github.com/GlobalWebIndex/kamon-stackdriver")
ThisBuild / credentials ++= sys.env.get("GITHUB_TOKEN").map(Credentials("GitHub Package Registry", "maven.pkg.github.com", "dmp-team", _))
