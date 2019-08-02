package kamon.stackdriver

import kamon.trace.Identifier.{Factory, Scheme}

object StackdriverScheme {
  def apply() = Scheme(Factory.SixteenBytesIdentifier, Factory.SixteenBytesIdentifier)
}
