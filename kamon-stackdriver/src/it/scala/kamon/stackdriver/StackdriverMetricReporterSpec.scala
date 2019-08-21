package kamon.stackdriver

import java.time.{Clock, Instant}

import com.google.monitoring.v3.{ListTimeSeriesRequest, TimeInterval, _}
import com.google.protobuf.Timestamp
import kamon.metric.PeriodSnapshot
import kamon.tag.TagSet
import kamon.testkit.MetricSnapshotBuilder
import org.scalatest.concurrent.Eventually
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

class StackdriverMetricReporterSpec extends FlatSpec with Matchers with Eventually {

  "StackdriverMetricReporter" should "report metric to GCP" in {
    val reporter = new StackdriverMetricReporter
//given
    val startedAt = Instant.now(Clock.systemUTC()).minusSeconds(10)
    val stackdriverMetricReporterValue = 34
    val tagSet = TagSet.builder().add("class", "StackdriverMetricReporter").build()
    val gauge = MetricSnapshotBuilder.gauge("test", tagSet, stackdriverMetricReporterValue)
    val snapshot = PeriodSnapshot (
      from = startedAt,
      to = startedAt.plusSeconds(10),
      counters = Seq.empty,
      gauges = Seq(gauge),
      histograms = Seq.empty,
      timers = Seq.empty,
      rangeSamplers = Seq.empty
    )
//when
    reporter.reportPeriodSnapshot(snapshot)
//than
    val timestamp = Timestamp
      .newBuilder()
      .setSeconds(startedAt.getEpochSecond)
      .setNanos(startedAt.getNano)
      .build()

    val interval = TimeInterval.newBuilder().setEndTime(timestamp).build()
    val view = ListTimeSeriesRequest.TimeSeriesView.FULL
    val request = ListTimeSeriesRequest.newBuilder()
      .setName(ProjectName.format("kamon-stackdriver"))
      .setFilter("""metric.type="custom.googleapis.com/kamon/test"""")
      .setInterval(interval)
      .setView(view)
      .build()

    eventually {
      val result = reporter.client.listTimeSeriesCallable().call(request)
      result.getTimeSeriesList.asScala should have size 1
      result.getTimeSeriesList.asScala.head.getPointsList.asScala should have size 1
      result.getTimeSeriesList.asScala.head.getPointsList.asScala.head.getValue.getDoubleValue should be(34)
      result.getTimeSeriesList.asScala.head.getMetric.getLabelsMap.asScala should contain("class" -> "StackdriverMetricReporter")
    }
  }
}
