package kamon.stackdriver

import ch.qos.logback.classic.spi.ILoggingEvent

class ExtraStackdriverEncoder extends StackdriverEncoder {
  override protected def extraData(event: ILoggingEvent): Map[String, String] =
    Map("extra_field" -> "extra_field_value")
}
