package kamon.stackdriver

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import kamon.Kamon
import kamon.context.Context
import kamon.tag.TagSet
import kamon.trace.Trace.SamplingDecision
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol._
import spray.json._

class StackdriverEncoderTest extends AnyFlatSpec with Matchers {

  Kamon.init()

  val logger: Logger = LoggerFactory.getLogger(classOf[StackdriverEncoderTest])

  it should "format correctly log as json" in {

    val contextEntry = Context.key[String]("context_entry", "default_context_entry_value")

    Kamon.runWithContextEntry(contextEntry, "context_entry_value") { // needs kamon.instrumentation.logback.mdc.copy.entries = ["context_entry"]
      Kamon.runWithContextTag("context_tag", "context_tag_value") {

        val span = Kamon.spanBuilder("operation").samplingDecision(SamplingDecision.Sample).start()
        Kamon.runWithSpan(span) {
          logger.info("This is a message: {}", "argument_value", new Exception("kaboom"): Any)
        }

        val str = RecordingAppender.logged.head

        val json   = str.parseJson.asJsObject
        val fields = json.fields

        withClue(json.prettyPrint) {
          fields("logging.googleapis.com/sourceLocation") shouldBe a[JsObject]
          fields("logging.googleapis.com/sourceLocation").asJsObject.fields.keySet shouldBe Set("file", "function", "line")
          fields("logging.googleapis.com/sourceLocation").asJsObject.fields("file").convertTo[String] shouldBe "kamon/stackdriver/StackdriverEncoderTest.scala"
          fields("logging.googleapis.com/spanId").convertTo[String] shouldBe span.id.string
          fields("logging.googleapis.com/trace").convertTo[String] should endWith(span.trace.id.string)
          fields("message").convertTo[String] should not be empty
          fields("message").convertTo[String] should startWith("This is a message: argument_value")
          fields("severity").convertTo[String] shouldBe "INFO"
          fields("timestamp") shouldBe a[JsObject]
          fields("timestamp").asJsObject.fields.keySet shouldBe Set("seconds", "nanos")

          //Available in MDC, but can be skipped as are provided in Stackdriver compatible way
          fields.keys should not contain "kamonSpanId"
          fields.keys should not contain "kamonTraceId"

          //Fields provided from StackdriverEncoder.extraData
          fields("extra_field").convertTo[String] shouldBe "extra_field_value"

          //Context entries provided as MDC and configured in kamon.instrumentation.logback.mdc.copy.entries = ["context_entry"]
          fields("context_entry").convertTo[String] shouldBe "context_entry_value"

          //Context tagx provided as MDC
          fields("context_tag").convertTo[String] shouldBe "context_tag_value"
        }
      }
    }
  }

}
