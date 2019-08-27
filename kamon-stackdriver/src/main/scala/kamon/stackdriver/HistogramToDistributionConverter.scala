package kamon.stackdriver

import com.google.api.Distribution
import com.google.api.Distribution.BucketOptions
import kamon.metric.Distribution.Bucket

import scala.collection.JavaConverters._

trait HistogramToDistributionConverter {
  protected def valueToBucketIndex(value: Long): Int
  def bucketOptions: BucketOptions

  @SuppressWarnings(Array("TraversableLast"))
  def histogramToDistributionValues(buckets: Seq[Bucket]): Seq[java.lang.Long] =
    if (buckets.isEmpty) {
      new Array[java.lang.Long](0)
    } else {
      val lastBucket = valueToBucketIndex(buckets.last.value)
      val results    = new Array[Long](lastBucket + 1) //use scala.Long as it defaults to 0

      buckets.foreach { bucket =>
        val index = valueToBucketIndex(bucket.value)
        results(index) += bucket.frequency
      }

      results.map(java.lang.Long.valueOf) //box values for Monitoring API
    }

  def histogramToDistribution(buckets: Seq[Bucket], count: Long): Distribution = {
    val values = histogramToDistributionValues(buckets)

    Distribution
      .newBuilder()
      .setBucketOptions(bucketOptions)
      .addAllBucketCounts(values.asJava)
      .setCount(count)
      .build()
  }
}

class ExponentialBucket(numFiniteBuckets: Int, growthFactor: Double, scale: Double) extends HistogramToDistributionConverter {
  private val bucketCount     = numFiniteBuckets + 2
  private val growthFactorLog = Math.log(growthFactor)

  protected def valueToBucketIndex(value: Long): Int = {
    // Lower bound (1 <= i < N): scale * (growthFactor ^ (i - 1))
    val result = (Math.log(value / scale) / growthFactorLog + 1.0).toInt
    //toInt correctly deals with values outside of the Int range for this use case

    if (result < 0) {
      0
    } else if (result >= bucketCount) {
      bucketCount - 1
    } else {
      result
    }
  }

  val bucketOptions: BucketOptions = {
    val exponentialBuckets = BucketOptions.Exponential
      .newBuilder()
      .setGrowthFactor(growthFactor)
      .setNumFiniteBuckets(numFiniteBuckets)
      .setScale(scale)
      .build()

    BucketOptions
      .newBuilder()
      .setExponentialBuckets(exponentialBuckets)
      .build()
  }

}

class LinearBucket(numFiniteBuckets: Int, width: Double, offset: Double) extends HistogramToDistributionConverter {
  private val bucketCount = numFiniteBuckets + 2

  protected def valueToBucketIndex(value: Long): Int = {
    // Lower bound (1 <= i < N): offset + (width * (i - 1))
    val result = ((value - offset) / width + 1.0).toInt

    if (result < 0) {
      0
    } else if (result >= bucketCount) {
      bucketCount - 1
    } else {
      result
    }
  }

  val bucketOptions: BucketOptions = {
    val linearBuckets = BucketOptions.Linear
      .newBuilder()
      .setNumFiniteBuckets(numFiniteBuckets)
      .setWidth(width)
      .setOffset(offset)
      .build()

    BucketOptions
      .newBuilder()
      .setLinearBuckets(linearBuckets)
      .build()
  }
}
