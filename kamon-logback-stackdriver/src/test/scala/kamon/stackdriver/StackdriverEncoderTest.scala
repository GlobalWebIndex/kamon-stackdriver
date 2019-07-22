package kamon.stackdriver

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import kamon.Kamon
import kamon.context.Context
import kamon.logback.instrumentation.ContextAwareLoggingEvent
import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

class StackdriverEncoderTest extends FlatSpec with Matchers {

  val encoder = new StackdriverEncoder {
    override protected def extraData(event: ILoggingEvent): Map[String, String] =
      Map("foo" -> "bar")
  }

  it should "format correctly log as json" in {

    Kamon.currentContext()
    val span = Kamon.buildSpan("operation").start()
    Kamon.withSpan(span, finishSpan = true) {
      val event: ILoggingEvent with ContextAwareLoggingEvent = new LoggingEvent(
        getClass.getName,
        LoggerFactory.getLogger(classOf[StackdriverEncoderTest]).asInstanceOf[ch.qos.logback.classic.Logger],
        Level.INFO,
        "This is a message {}",
        new Exception("kaboom"),
        Array("bar")
      ) with ContextAwareLoggingEvent {
        private[this] var ctx: Option[Context] = None

        override def getContext: Context = ctx.orNull

        override def setContext(context: Context): Unit = ctx = Some(context)
      }

      event.setContext(Kamon.currentContext())

      val str = new String(encoder.encode(event))
      val json = str.parseJson.asJsObject
      val fields = json.fields

      withClue(json.prettyPrint) {
        fields("logging.googleapis.com/sourceLocation") shouldBe a[JsObject]
        fields("logging.googleapis.com/sourceLocation").asJsObject.fields.keySet shouldBe Set("file", "function", "line")
        fields("logging.googleapis.com/spanId").convertTo[String] shouldBe Kamon.currentSpan().context().spanID.string
        fields("logging.googleapis.com/trace").convertTo[String] should endWith(Kamon.currentSpan().context().traceID.string)
        fields("message").convertTo[String] should not be empty
        fields("severity").convertTo[String] shouldBe "INFO"
        fields("timestamp") shouldBe a[JsObject]
        fields("timestamp").asJsObject.fields.keySet shouldBe Set("seconds", "nanos")
      }
    }

  }

}
