package kamon.stackdriver

import kamon.stackdriver.StackdriverMarker.LogValue.{BooleanValue, NestedValue, NullValue, NumberValue, StringValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BasicStackdriverMarkerTest extends AnyWordSpec with Matchers {

  private def builder = JsonStringBuilder.getSingleThreaded

  "BasicStackdriverMarker" should {
    "encode log values to json" when {
      "given null value" in {
        BasicStackdriverMarker("markerName", NullValue).encode(builder).result shouldBe """"markerName":null"""
      }
      "given int value" in {
        BasicStackdriverMarker("markerName", NumberValue(1)).encode(builder).result shouldBe """"markerName":1"""
      }
      "given bool value" in {
        BasicStackdriverMarker("markerName", BooleanValue(true)).encode(builder).result shouldBe """"markerName":true"""
        BasicStackdriverMarker("markerName", BooleanValue(false)).encode(builder).result shouldBe """"markerName":false"""
      }
      "given string value" in {
        BasicStackdriverMarker("markerName", StringValue("foo")).encode(builder).result shouldBe """"markerName":"foo""""
      }
      "given string and simple nested value" in {
        BasicStackdriverMarker("markerName", NestedValue(Map("foo" -> StringValue("bar"))))
          .encode(builder)
          .result shouldBe """"markerName":{"foo":"bar"}"""
      }
      "given multiple values in nested value" in {
        BasicStackdriverMarker(
          "markerName",
          NestedValue(Map("foo" -> StringValue("bar"), "bar" -> NullValue, "baz" -> BooleanValue(true), "meh" -> NumberValue(1)))
        ).encode(builder).result shouldBe """"markerName":{"foo":"bar","bar":null,"baz":true,"meh":1}"""
      }
      "given multiple nested values" in {
        BasicStackdriverMarker(
          "markerName",
          NestedValue(Map("foo" -> NestedValue(Map("bar" -> StringValue("baz")))))
        ).encode(builder).result shouldBe """"markerName":{"foo":{"bar":"baz"}}""".stripMargin
      }
    }
  }
}
