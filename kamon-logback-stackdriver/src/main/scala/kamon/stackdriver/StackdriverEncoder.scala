package kamon.stackdriver

import java.time.Instant

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.pattern.{RootCauseFirstThrowableProxyConverter, ThrowableProxyConverter}
import ch.qos.logback.classic.spi.{CallerData, ILoggingEvent}
import ch.qos.logback.core.encoder.EncoderBase
import com.google.cloud.ServiceOptions
import kamon.Kamon
import kamon.trace.Span

import scala.collection.JavaConverters._
import scala.util.Try

class StackdriverEncoder extends EncoderBase[ILoggingEvent] {

  private[this] val projectId = Try(ServiceOptions.getDefaultProjectId()).toOption.getOrElse("unknown")

  private[this] val MessageFieldName        = "message"
  private[this] val SeverityFieldName       = "severity"
  private[this] val TimestampFieldName      = "timestamp"
  private[this] val TraceIdFieldName        = "logging.googleapis.com/trace"
  private[this] val SpanIdFieldName         = "logging.googleapis.com/spanId"
  private[this] val SourceLocationFieldName = "logging.googleapis.com/sourceLocation"

  override def headerBytes(): Array[Byte] = Array.empty

  private[this] var tpc: ThrowableProxyConverter = new RootCauseFirstThrowableProxyConverter()
  tpc.setOptionList(List("full").asJava)
  tpc.start()

  override def encode(event: ILoggingEvent): Array[Byte] = {
    val builder = JsonStringBuilder.getSingleThreaded
    builder.`{`
    severity(builder, event)
    message(builder, event)
    sourceLocation(builder, event)
    traceInformation(builder, event)
    encodeExtraData(builder, event)
    eventTime(builder, event)

    builder.`}`.appendNewline().result.getBytes

  }

  protected def sourceLocation(builder: JsonStringBuilder, event: ILoggingEvent): JsonStringBuilder = {
    val cda: Array[StackTraceElement] = event.getCallerData
    builder.encodeStringRaw(SourceLocationFieldName).`:`.`{`
    if (cda != null && cda.length > 0) {
      val ste = cda(0)

      builder
        .encodeStringRaw("function")
        .`:`
        .startString()
        .appendEncodedString(ste.getClassName.stripSuffix("$"))
        .appendEncodedString(".")
        .appendEncodedString(ste.getMethodName)
        .endString()
        .`,`
      builder.encodeStringRaw("file").`:`
      var pkg = ste.getClassName.replaceAll("\\.", "/")
      pkg =
        pkg + ste.getFileName
      builder.startString().appendEncodedString(pkg.substring(0, pkg.lastIndexOf("/") + 1)).appendEncodedString(ste.getFileName).endString().`,`

      builder.encodeStringRaw("line").`:`.encodeNumber(ste.getLineNumber)
    } else {
      builder
        .encodeStringRaw("function")
        .`:`
        .encodeStringRaw(CallerData.NA)
        .`,`
        .encodeStringRaw("file")
        .`:`
        .encodeStringRaw(CallerData.NA)
        .`,`
        .encodeStringRaw("line")
        .`:`
        .encodeNumber(CallerData.LINE_NA)
    }
    builder.`}`.`,`
  }

  protected def eventTime(builder: JsonStringBuilder, event: ILoggingEvent): JsonStringBuilder = {
    val ts = Instant.ofEpochMilli(event.getTimeStamp)
    builder
      .encodeStringRaw(TimestampFieldName)
      .`:`
      .`{`
      .encodeStringRaw("seconds")
      .`:`
      .encodeNumber(ts.getEpochSecond)
      .`,`
      .encodeStringRaw("nanos")
      .`:`
      .encodeNumber(ts.getNano)
      .`}`
  }

  private[this] def severity(builder: JsonStringBuilder, event: ILoggingEvent): JsonStringBuilder =
    builder
      .encodeStringRaw(SeverityFieldName)
      .`:`
      .encodeStringRaw(event.getLevel match {
        case Level.ALL   => "DEBUG"
        case Level.TRACE => "DEBUG"
        case Level.DEBUG => "DEBUG"
        case Level.INFO  => "INFO"
        case Level.WARN  => "WARNING"
        case Level.ERROR => "ERROR"
        case _           => "DEFAULT"
      })
      .`,`

  private[this] def message(builder: JsonStringBuilder, event: ILoggingEvent): JsonStringBuilder = {
    builder.encodeStringRaw(MessageFieldName).`:`
    val message    = event.getFormattedMessage
    val stackTrace = tpc.convert(event)
    if (stackTrace != null && stackTrace.nonEmpty) {
      builder.startString().appendEncodedString(message).appendEncodedString("\n").appendEncodedString(stackTrace).endString().`,`
    } else {
      builder.encodeString(message).`,`
    }
  }

  private[this] def traceInformation(builder: JsonStringBuilder, event: ILoggingEvent): JsonStringBuilder = {
    val ctx  = Kamon.currentContext()
    val span = ctx.get(Span.Key)
    if (span.trace.id.string.nonEmpty && span.id.string.nonEmpty) {
      builder
        .encodeStringRaw(TraceIdFieldName)
        .`:`
        .startString()
        .appendEncodedString("projects/")
        .appendEncodedString(projectId)
        .appendEncodedString("/traces/")
        .appendEncodedString(span.trace.id.string)
        .endString()
        .`,`
      builder.encodeStringRaw(SpanIdFieldName).`:`.encodeString(span.id.string).`,`
    }
    builder
  }

  @SuppressWarnings(Array("UnusedMethodParameter"))
  protected def extraData(event: ILoggingEvent): Map[String, String] = Map.empty

  private[this] def encodeExtraData(builder: JsonStringBuilder, event: ILoggingEvent): JsonStringBuilder = {
    val data = extraData(event).iterator
    while (data.hasNext) {
      val (key, value) = data.next()
      builder.encodeString(key).`:`.encodeString(value)
      builder.`,`
    }
    builder
  }

  override def footerBytes(): Array[Byte] = Array.empty
}
