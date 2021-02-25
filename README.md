# Kamon-Stackdriver

Kamon-Stackdriver is a library to report metrics collected by [Kamon](https://github.com/kamon-io/Kamon) to
[Google Stackdriver](https://cloud.google.com/stackdriver/). It supports both
[Trace](https://cloud.google.com/trace/docs/) and [Monitoring](https://cloud.google.com/monitoring/docs/).

### Getting Started

Add `sbt-github-packages` plugin :
```sbt
addSbtPlugin("com.codecommit"            % "sbt-github-packages" % "0.5.2")
```
Add resolver and dependencies :
```sbt
resolvers += Resolver.githubPackages("GlobalWebIndex")
libraryDependencies ++= Seq(
  "io.kamon" %% "kamon-stackdriver"         % "<version>",
  "io.kamon" %% "kamon-logback-stackdriver" % "<version>"
)
```

### Kamon Configuration
The following Kamon configuration is required:
```hocon
kamon {
  trace {
    # Make the identifiers compatible with what Stackdriver Trace expects.
    identifier-scheme = double
  }
}
```

The following Kamon configuration is recommended:
```hocon
kamon {
  metric {
    # Stackdriver accepts at most a tick every minute
    tick-interval = 1 minute
  }
  trace {
    # The Stackdriver rate limit is at a 1000 requests per 100 seconds, so
    # sending frequently isn't a problem.
    tick-interval = 2 seconds
  }
}
```

### Logback encoder
To configure Logback so it outputs messages in Stackdriver compatible logging format add to your `logback.xml` encoder:

```xml
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="kamon.stackdriver.StackdriverEncoder">
        </encoder>
    </appender>
```    
If you want to output some additional information in each log entry you can also extend `StackdriverEncoder` and use it as Encoder in the configuration:

```scala
class MyStackdriverEncoder extends StackdriverEncoder {

  override protected def extraData(event: ILoggingEvent): Map[String, String] = event match {
    case ctxAware: ContextAwareLoggingEvent =>
      val ctx = ctxAware.getContext
      ???
    case _ => Map("foo" -> "bar", "region" -> "europe-west1")
  }
}
```

### Library configuration

See `reference.conf`. In all cases the `kamon.stackdriver.metric.resource` property has to be updated to reflect for what resource
you're collecting metrics.

## Tracing labels mappings

Stackdriver tracing treats some the labels specially, a mapping can be set in the configuration. Default mappings are shown below. 
More information you can find in [Google Stackdriver TraceSpan](https://cloud.google.com/trace/docs/reference/v1/rest/v1/projects.traces#TraceSpan) documentation.

```hocon
kamon.stackdrvier.span {
  tags {
    mappings {
      "http.method" = "/http/method"
      "http.status_code" = "/http/status_code"
    }
  }
}
```

## License

This software is licensed under the Apache 2 license, quoted below.

```Copyright © 2017 Mark van der Tol

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

    [http://www.apache.org/licenses/LICENSE-2.0]

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
```

This project isn't associated with Google, Stackdriver or Kamon.