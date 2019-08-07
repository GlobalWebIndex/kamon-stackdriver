package kamon.stackdriver

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import kamon.Kamon
import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._
import kamon.trace.Trace.SamplingDecision

class StackdriverEncoderTest extends FlatSpec with Matchers {

  val encoder = new StackdriverEncoder {
    override protected def extraData(event: ILoggingEvent): Map[String, String] =
      Map("foo" -> "bar")
  }

  it should "format correctly log as json" in {

    Kamon.currentContext()
    val span = Kamon.spanBuilder("operation").samplingDecision(SamplingDecision.Sample).start()
    val event: ILoggingEvent = Kamon.runWithSpan(span, finishSpan = true) {
      new LoggingEvent(
        getClass.getName,
        LoggerFactory.getLogger(classOf[StackdriverEncoderTest]).asInstanceOf[ch.qos.logback.classic.Logger],
        Level.INFO,
        "This is a message {}",
        new Exception("kaboom"),
        Array("bar")
      )
    }
      val str    = new String(encoder.encode(event))
      val json   = str.parseJson.asJsObject
      val fields = json.fields

      withClue(json.prettyPrint) {
        fields("logging.googleapis.com/sourceLocation") shouldBe a[JsObject]
        fields("logging.googleapis.com/sourceLocation").asJsObject.fields.keySet shouldBe Set("file", "function", "line")
        fields("logging.googleapis.com/spanId").convertTo[String] shouldBe span.id.string
        fields("logging.googleapis.com/trace").convertTo[String] should endWith(span.trace.id.string)
        fields("message").convertTo[String] should not be empty
        fields("severity").convertTo[String] shouldBe "INFO"
        fields("timestamp") shouldBe a[JsObject]
        fields("timestamp").asJsObject.fields.keySet shouldBe Set("seconds", "nanos")
      }
    

  }

}
