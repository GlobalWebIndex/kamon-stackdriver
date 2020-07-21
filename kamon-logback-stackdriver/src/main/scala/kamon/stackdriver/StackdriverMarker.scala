package kamon.stackdriver

import java.util

import kamon.stackdriver.StackdriverMarker._
import org.slf4j.Marker

import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._

abstract class StackdriverMarker extends Marker {
  private val markers = TrieMap.empty[String, Marker]

  def values: Map[String, LogValue] =
    markers.collect {
      case (name, marker: StackdriverMarker) =>
        name -> marker.value
    }.toMap

  def name: String
  def value: LogValue

  def encode(implicit enc: LogValue.Encoder): String = enc.encode(value)

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

    trait Encoder {
      def encode(logValue: LogValue): String
    }

    implicit object JsonEncoder extends Encoder {
      private def prefixWithComma(counter: Int, totalCount: Int, builder: JsonStringBuilder) = {
        val encodedWithComma = if (counter == totalCount) builder else builder.`,`
        counter + 1 -> encodedWithComma
      }
      private def jsonStringBuilder(value: LogValue, build: JsonStringBuilder): JsonStringBuilder =
        value match {
          case NestedValue(nested) =>
            val elementsCount = nested.size
            nested
              .foldLeft((1, build.`{`)) {
                case ((counter, acc), (k, StringValue(v))) =>
                  prefixWithComma(counter, elementsCount, acc.encodeStringRaw(k).`:`.encodeStringRaw(v))
                case ((counter, acc), (k, NumberValue(v))) =>
                  prefixWithComma(counter, elementsCount, acc.encodeStringRaw(k).`:`.appendString(v.toString))
                case ((counter, acc), (k, NullValue)) =>
                  prefixWithComma(counter, elementsCount, acc.encodeStringRaw(k).`:`.appendString("null"))
                case ((counter, acc), (k, BooleanValue(b))) =>
                  val stringBoolVal = if (b) "true" else "false"
                  prefixWithComma(counter, elementsCount, acc.encodeStringRaw(k).`:`.appendString(stringBoolVal))
                case ((counter, acc), (k, nv @ NestedValue(_))) =>
                  prefixWithComma(counter, elementsCount, jsonStringBuilder(nv, acc.encodeStringRaw(k).`:`))
              }
              ._2 `}`
          case StringValue(v) =>
            build.encodeStringRaw(v)
          case NumberValue(v) =>
            build.appendString(v.toString)
          case NullValue =>
            build.appendString("null")
          case BooleanValue(b) =>
            if (b) build.appendString("true") else build.appendString("false")
        }

      override def encode(value: LogValue): String = jsonStringBuilder(value, JsonStringBuilder.getSingleThreaded).result
    }
  }
}

case class BasicStackdriverMarker(name: String, value: LogValue) extends StackdriverMarker
