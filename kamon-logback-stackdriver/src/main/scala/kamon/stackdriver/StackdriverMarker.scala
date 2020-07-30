package kamon.stackdriver

import java.util

import kamon.stackdriver.StackdriverMarker.LogValue.JsonWriter
import kamon.stackdriver.StackdriverMarker._
import org.slf4j.Marker

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

abstract class StackdriverMarker extends Marker {
  private val markers = TrieMap.empty[String, Marker]

  protected def writer: LogValue.Writer = JsonWriter // override if needed

  def name: String
  def value: LogValue

  def encode(builder: JsonStringBuilder): JsonStringBuilder = writer.write(name, builder, value)

  override def getName: String                   = name
  override def add(marker: Marker): Unit         = markers.put(marker.getName, marker)
  override def remove(marker: Marker): Boolean   = markers.remove(marker.getName).isDefined
  override def hasChildren: Boolean              = markers.nonEmpty
  override def hasReferences: Boolean            = markers.nonEmpty
  override def iterator(): util.Iterator[Marker] = markers.values.iterator.asJava
  override def contains(marker: Marker): Boolean = this == marker || markers.contains(marker.getName)
  override def contains(name: String): Boolean   = markers.contains(name)
}

object StackdriverMarker {
  sealed trait LogValue
  object LogValue {
    final case object NullValue                                 extends LogValue
    final case class StringValue(v: String)                     extends LogValue
    final case class BooleanValue(v: Boolean)                   extends LogValue
    final case class NumberValue(v: Long)                       extends LogValue
    final case class NestedValue(nested: Map[String, LogValue]) extends LogValue

    trait Writer {
      def write(name: String, builder: JsonStringBuilder, logValue: LogValue): JsonStringBuilder
    }

    object JsonWriter extends Writer {

      private def jsonStringBuilder(value: LogValue, builder: JsonStringBuilder): Unit =
        value match {
          case StringValue(v) =>
            builder.encodeString(v)
          case NumberValue(v) =>
            builder.encodeNumber(v)
          case NullValue =>
            builder.encodeNull()
          case BooleanValue(b) =>
            builder.encodeBoolean(b)
          case NestedValue(nested) =>
            val elementsCount = nested.size
            nested
              .foldLeft((1, builder.`{`)) {
                case ((counter, acc), (k, v)) =>
                  jsonStringBuilder(v, acc.encodeString(k).`:`)
                  val encodedWithComma = if (counter == elementsCount) acc else acc.`,`
                  counter + 1 -> encodedWithComma
              }
              ._2 `}`
        }

      override def write(name: String, builder: JsonStringBuilder, value: LogValue): JsonStringBuilder = {
        jsonStringBuilder(value, builder.encodeString(name).`:`)
        builder
      }
    }
  }
}

case class BasicStackdriverMarker(name: String, value: LogValue) extends StackdriverMarker
