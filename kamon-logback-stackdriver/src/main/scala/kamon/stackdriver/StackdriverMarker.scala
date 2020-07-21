package kamon.stackdriver

import java.util

import kamon.stackdriver.StackdriverMarker._
import org.slf4j.Marker

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._

abstract class StackdriverMarker(implicit enc: LogValue.Writer) extends Marker {
  private val markers = TrieMap.empty[String, Marker]

  def values: Map[String, LogValue] =
    markers.collect {
      case (name, marker: StackdriverMarker) =>
        name -> marker.value
    }.toMap

  def name: String
  def value: LogValue

  def encode(builder: JsonStringBuilder): JsonStringBuilder = enc.write(name, builder, value)

  override def getName: String                   = name
  override def add(marker: Marker): Unit         = markers.addOne(marker.getName -> marker)
  override def remove(marker: Marker): Boolean   = markers.remove(marker.getName).isDefined
  override def hasChildren: Boolean              = markers.isEmpty
  override def hasReferences: Boolean            = markers.isEmpty
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
    final case class NumberValue(v: Int)                        extends LogValue
    final case class NestedValue(nested: Map[String, LogValue]) extends LogValue

    trait Writer {
      def write(name: String, builder: JsonStringBuilder, logValue: LogValue): JsonStringBuilder
    }

    implicit object JsonEncoder extends Writer {

      private def jsonStringBuilder(value: LogValue, builder: JsonStringBuilder): Unit =
        value match {
          case StringValue(v) =>
            builder.encodeStringRaw(v)
          case NumberValue(v) =>
            builder.appendString(v.toString)
          case NullValue =>
            builder.appendString("null")
          case BooleanValue(b) =>
            if (b) builder.appendString("true") else builder.appendString("false")
          case NestedValue(nested) =>
            val elementsCount = nested.size
            nested
              .foldLeft((1, builder.`{`)) {
                case ((counter, acc), (k, v)) =>
                  jsonStringBuilder(v, acc.encodeStringRaw(k).`:`)
                  val encodedWithComma = if (counter == elementsCount) acc else acc.`,`
                  counter + 1 -> encodedWithComma
              }
              ._2 `}`
        }

      override def write(name: String, builder: JsonStringBuilder, value: LogValue): JsonStringBuilder = {
        jsonStringBuilder(value, builder.encodeStringRaw(name).`:`)
        builder
      }
    }
  }
}

case class BasicStackdriverMarker(name: String, value: LogValue) extends StackdriverMarker
