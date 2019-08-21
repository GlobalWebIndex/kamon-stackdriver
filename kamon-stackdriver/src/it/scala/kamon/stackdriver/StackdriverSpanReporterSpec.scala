package kamon.stackdriver

import java.time.{Clock, Instant}

import kamon.tag.TagSet
import kamon.trace.Identifier.Factory._
import kamon.trace.Span.Finished
import kamon.trace.{Span, Trace}
import kamon.trace.Trace.SamplingDecision
import org.scalatest.concurrent.Eventually
import org.scalatest.{FlatSpec, Matchers}

class StackdriverSpanReporterSpec extends FlatSpec with Matchers with Eventually {

  "StackdriverSpanReporter" should "report span to GCP" in {
    val reporter = new StackdriverSpanReporter

    val startedAt = Instant.now(Clock.systemUTC()).minusSeconds(1)
    val finishedSpans = Seq(Finished (
      id = EightBytesIdentifier.generate(),
      trace = Trace(SixteenBytesIdentifier.generate(), SamplingDecision.Sample),
      parentId = EightBytesIdentifier.generate(),
      operationName = "test",
      hasError = false,
      wasDelayed = false,
      from = startedAt,
      to = startedAt.plusMillis(118),
      kind = Span.Kind.Server,
      position = Span.Position.Root,
      tags = TagSet.builder().add("class", "StackdriverSpanReporter").build(),
      metricTags = TagSet.builder().add("spec", "StackdriverSpanReporterSpec").build(),
      marks = Seq.empty,
      links = Seq.empty
    ))

    //The v2 API only supports sending trace data. There are no methods to retrieve the data.
    reporter.reportSpans(finishedSpans)

    //verify on the UI or implement rest call to the Google Trace API

  }
}
