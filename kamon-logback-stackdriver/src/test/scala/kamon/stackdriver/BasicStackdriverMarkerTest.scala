package kamon.stackdriver

import kamon.stackdriver.StackdriverMarker.LogValue.{BooleanValue, NestedValue, NullValue, NumberValue, StringValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BasicStackdriverMarkerTest extends AnyWordSpec with Matchers {

  "BasicStackdriverMarker" should {
    "encode log values to json" when {
      "given null value" in {
        BasicStackdriverMarker("blala", NullValue).encode shouldBe """null"""
      }
      "given int value" in {
        BasicStackdriverMarker("blala", NumberValue(1)).encode shouldBe """1"""
      }
      "given bool value" in {
        BasicStackdriverMarker("blala", BooleanValue(true)).encode shouldBe """true"""
        BasicStackdriverMarker("blala", BooleanValue(false)).encode shouldBe """false"""
      }
      "given string value" in {
        BasicStackdriverMarker("blala", StringValue("foo")).encode shouldBe """"foo""""
      }
      "given string and simple nested value" in {
        BasicStackdriverMarker("blala", NestedValue(Map("foo" -> StringValue("bar")))).encode shouldBe """{"foo":"bar"}"""
      }
      "given multiple values in nested value" in {
        BasicStackdriverMarker(
          "blala",
          NestedValue(Map("foo" -> StringValue("bar"), "bar" -> NullValue, "baz" -> BooleanValue(true), "meh" -> NumberValue(1)))
        ).encode shouldBe """{"foo":"bar","bar":null,"baz":true,"meh":1}"""
      }
      "given multiple nested values" in {
        BasicStackdriverMarker(
          "blala",
          NestedValue(Map("foo" -> NestedValue(Map("bar" -> StringValue("baz")))))
        ).encode shouldBe """{"foo":{"bar":"baz"}}""".stripMargin
      }
    }
  }
}
