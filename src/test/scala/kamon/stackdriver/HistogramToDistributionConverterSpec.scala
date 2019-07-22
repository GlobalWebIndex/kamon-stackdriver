package kamon.stackdriver

import kamon.metric.Bucket
import org.scalatest.{FlatSpec, Matchers}

class HistogramToDistributionConverterSpec extends FlatSpec with Matchers {

  val eb = new ExponentialBucket(10, 1.5, 1.0)

  "ExponentialBucket" should "Put small values in the first bucket" in {
    val buckets  = Vector(TestBucket(0, 5))
    val expected = List(5L)

    eb.histogramToDistributionValues(buckets).toList shouldBe expected
  }

  it should "Put large values in the last bucket" in {
    val buckets  = Vector(TestBucket(100, 5))
    val expected = Range(0, 11).map(_ => 0L) :+ 5L

    eb.histogramToDistributionValues(buckets).toList shouldBe expected
  }

  it should "Correctly interpolate values into right bucket" in {
    val buckets  = Vector(TestBucket(35, 5))
    val expected = List(0, 0, 0, 0, 0, 0, 0, 0, 0, 5)

    eb.histogramToDistributionValues(buckets).toList shouldBe expected
  }

  val lb = new LinearBucket(numFiniteBuckets = 10, width = 2.0, offset = 1.0)
  "LinearBucket" should "Put small values in the first bucket" in {
    val buckets  = Vector(TestBucket(0, 5))
    val expected = List(5L)

    lb.histogramToDistributionValues(buckets).toList shouldBe expected
  }

  it should "Put large values in the last bucket" in {
    val buckets  = Vector(TestBucket(25, 5))
    val expected = Range(0, 11).map(_ => 0L) :+ 5L

    lb.histogramToDistributionValues(buckets).toList shouldBe expected
  }

  it should "Correctly interpolate values into right bucket" in {
    val buckets  = Vector(TestBucket(5, 5))
    val expected = List(0, 0, 0, 5)

    lb.histogramToDistributionValues(buckets).toList shouldBe expected
  }
}

case class TestBucket(value: Long, frequency: Long) extends Bucket
