package kamon.stackdriver

import java.nio.charset.Charset

import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.ConsoleAppender

class RecordingAppender extends ConsoleAppender[LoggingEvent] {
  override def append(loggingEvent: LoggingEvent): Unit =
    RecordingAppender.logged = new String(encoder.encode(loggingEvent), Charset.forName("utf8")) :: RecordingAppender.logged
}

object RecordingAppender {
  @transient
  var logged: List[String] = List.empty
}
