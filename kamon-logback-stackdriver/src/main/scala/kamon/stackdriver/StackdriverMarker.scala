package kamon.stackdriver

import java.util

import kamon.stackdriver.StackdriverAuditMarker.ActionType.ActionType
import kamon.stackdriver.StackdriverAuditMarker.Audit
import kamon.stackdriver.StackdriverMarker.LogValue.{NestedValue, NumberValue, StringValue}
import kamon.stackdriver.StackdriverMarker._
import org.slf4j.Marker

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
    final case class NumberValue(v: Long)                       extends LogValue
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

case class StackdriverAuditMarker(audit: Audit) extends StackdriverMarker {
  val name = "audit"
  val value: LogValue =
    NestedValue(
      Map(
        "caller" -> NestedValue(
          Map(
            "gwi_org_id"  -> NumberValue(audit.caller.gwiOrgId),
            "gwi_user_id" -> NumberValue(audit.caller.gwiUserId)
          )
        ),
        "action" -> NestedValue(
          Map(
            "type"    -> StringValue(audit.action.`type`.toString),
            "message" -> StringValue(audit.action.message)
          )
        ),
        "target" -> NestedValue(
          Map(
            "type" -> StringValue(audit.target.`type`),
            "name" -> StringValue(audit.target.name),
            "id"   -> NumberValue(audit.target.id)
          )
        )
      )
    )
}

object StackdriverAuditMarker {
  object ActionType extends Enumeration {
    type ActionType = Value
    val Create, Update, Delete = Value
  }
  case class Caller(gwiOrgId: Int, gwiUserId: Int)
  case class Action(`type`: ActionType, message: String)
  case class Target(`type`: String, id: Long, name: String)
  case class Audit(caller: Caller, action: Action, target: Target)
}
