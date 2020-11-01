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
    final case object NullValue extends LogValue {
      override val productPrefix: String = "LogValue.NullValue"
    }
    final case class StringValue(v: String) extends LogValue {
      override val productPrefix: String = "LogValue.StringValue"
    }
    final case class BooleanValue(v: Boolean) extends LogValue {
      override val productPrefix: String = "LogValue.BooleanValue"
    }
    final case class NumberValue(v: Long) extends LogValue {
      override val productPrefix: String = "LogValue.NumberValue"
    }
    final case class NestedValue(nested: Map[String, LogValue]) extends LogValue {
      override val productPrefix: String = "LogValue.NestedValue"
    }
    object NestedValue {
      def apply(pairs: (String, LogValue)*): NestedValue = NestedValue(pairs.toMap)
    }
    final case class ArrayValue(elements: Vector[LogValue]) extends LogValue {
      override val productPrefix: String = "LogValue.ArrayValue"
    }
    object ArrayValue {
      def apply(elements: LogValue*): ArrayValue = new ArrayValue(elements.toVector)
    }

    trait Writer {
      def write(name: String, builder: JsonStringBuilder, logValue: LogValue): JsonStringBuilder
    }

    object JsonWriter extends Writer {

      private[this] def writeValue(logValue: LogValue, builder: JsonStringBuilder): Unit =
        logValue match {
          case StringValue(v) =>
            builder.encodeString(v)
          case NumberValue(v) =>
            builder.encodeNumber(v)
          case NullValue =>
            builder.encodeNull()
          case BooleanValue(b) =>
            builder.encodeBoolean(b)
          case NestedValue(nested) =>
            var i = 0
            builder.`{`
            nested.foreach {
              case (key, value) =>
                if (i > 0) builder.`,`
                builder.encodeString(key).`:`
                writeValue(value, builder)
                i = i + 1
            }
            builder.`}`
          case ArrayValue(elements) =>
            var i = 0
            builder.`[`
            elements.foreach { value =>
              if (i > 0) builder.`,`
              writeValue(value, builder)
              i = i + 1
            }
            builder.`]`
        }

      override def write(name: String, builder: JsonStringBuilder, value: LogValue): JsonStringBuilder = {
        writeValue(value, builder.encodeString(name).`:`)
        builder
      }
    }
  }
}

case class BasicStackdriverMarker(name: String, value: LogValue) extends StackdriverMarker
