package kamon.stackdriver

import kamon.trace.Identifier.{Factory, Scheme}

object StackdriverScheme {
  def apply() = Scheme(traceIdFactory = Factory.SixteenBytesIdentifier, spanIdFactory = Factory.SixteenBytesIdentifier)
}
