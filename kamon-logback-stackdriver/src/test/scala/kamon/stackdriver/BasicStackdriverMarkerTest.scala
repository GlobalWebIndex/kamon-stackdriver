package kamon.stackdriver

import kamon.stackdriver.StackdriverMarker.LogValue.{NestedValue, StringValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BasicStackdriverMarkerTest extends AnyWordSpec with Matchers {

  "BasicStackdriverMarker" should {
    "encode log values to json" when {
      "given string value" in {
        BasicStackdriverMarker("blala", StringValue("foo")).encode shouldBe """"foo""""
      }
      "given string and simple nested value" in {
        BasicStackdriverMarker("blala", NestedValue(Map("foo" -> StringValue("bar")))).encode shouldBe """{"foo":"bar"}"""
      }
      "given multiple string values" in {
        BasicStackdriverMarker(
          "blala",
          NestedValue(Map("foo" -> StringValue("bar"), "bar" -> StringValue("baz")))
        ).encode shouldBe """{"foo":"bar","bar":"baz"}"""
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
