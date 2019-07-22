package kamon.stackdriver

import java.util

import com.google.api.MetricDescriptor.MetricKind
import com.google.api.{Metric, MonitoredResource}
import com.google.cloud.ServiceOptions
import com.google.cloud.monitoring.v3.{MetricServiceClient, MetricServiceSettings}
import com.google.monitoring.v3._
import com.typesafe.config.Config
import kamon.metric.{MetricDistribution, MetricValue, PeriodSnapshot}
import kamon.util.CallingThreadExecutionContext
import kamon.{Kamon, MetricReporter}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class StackdriverMetricReporter extends MetricReporter {

  private val logger = LoggerFactory.getLogger(getClass)

  private val maxTimeseriesPerRequest = 100

  private implicit def ec: ExecutionContext = CallingThreadExecutionContext

  private var client: MetricServiceClient = _

  private var projectId: String                                                  = _
  private var histogramToDistributionConverter: HistogramToDistributionConverter = _
  private var resource: MonitoredResource                                        = _

  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val interval = TimeInterval
      .newBuilder()
      .setEndTime(instantToTimestamp(snapshot.from))
      .build()

    val histogramSeries = snapshot.metrics.histograms.map(v => histogram(v, interval))
    val counterSeries   = snapshot.metrics.counters.map(v => counters(v, interval))
    val gaugeSeries     = snapshot.metrics.gauges.map(v => counters(v, interval))
    val minMaxSeries    = snapshot.metrics.rangeSamplers.map(v => histogram(v, interval))

    val allSeries: Seq[TimeSeries] = histogramSeries ++ counterSeries ++ gaugeSeries ++ minMaxSeries

    if (allSeries.nonEmpty) {
      val allSeriesSplit = allSeries.grouped(maxTimeseriesPerRequest)

      allSeriesSplit.foreach { series =>
        val request = CreateTimeSeriesRequest
          .newBuilder()
          .addAllTimeSeries(series.asJava)
          .setName(ProjectName.format(projectId))
          .build()
        writeSeries(request)
      }
    }
  }

  private[this] def readResource(config: Config): Unit = {
    val resourceLabels = config
      .getConfig("labels")
      .entrySet()
      .asScala
      .map { entry =>
        entry.getKey -> entry.getValue.unwrapped().toString
      }
      .toMap

    resource = MonitoredResource
      .newBuilder()
      .setType(config.getString("type"))
      .putAllLabels(resourceLabels.asJava)
      .build()
  }

  private[this] def writeSeries(timeSeriesRequest: CreateTimeSeriesRequest): Unit =
    client.createTimeSeriesCallable().futureCall(timeSeriesRequest).onComplete {
      case Success(_) => //ok
      case Failure(e) => logger.error("Failed to send TimeSeries", e)
    }

  private[this] val sanitizationRegexp = """[^\w]""".r

  private[this] def sanitizeTags(tags: kamon.Tags): util.Map[String, String] = {
    val res = new util.HashMap[String, String]()
    tags.foreach {
      case (key, value) =>
        res.put(sanitizationRegexp.replaceAllIn(key, "_"), value)
    }
    res
  }

  private def newTimeSeries(name: String, tags: kamon.Tags, typedValue: TypedValue, timeInterval: TimeInterval) = {
    val point = Point
      .newBuilder()
      .setValue(typedValue)
      .setInterval(timeInterval)
      .build()

    val fullMetricType = "custom.googleapis.com/kamon/" + name.replace('.', '/')
    val metric = Metric
      .newBuilder()
      .setType(fullMetricType)
      .putAllLabels(sanitizeTags(tags))
      .build()

    TimeSeries
      .newBuilder()
      .setMetric(metric)
      .addPoints(point)
      .setMetricKind(MetricKind.GAUGE)
      .setResource(resource)
      .build()
  }

  def histogram(v: MetricDistribution, timeInterval: TimeInterval): TimeSeries = {
    val distribution = histogramToDistributionConverter.histogramToDistribution(v.distribution.buckets, v.distribution.count)

    val typedValue = TypedValue
      .newBuilder()
      .setDistributionValue(distribution)
      .build()
    newTimeSeries(v.name, v.tags, typedValue, timeInterval)
  }

  def counters(v: MetricValue, timeInterval: TimeInterval): TimeSeries = {
    val typedValue = TypedValue
      .newBuilder()
      .setInt64Value(v.value)
      .build()

    newTimeSeries(v.name, v.tags, typedValue, timeInterval)
  }

  private def configureDistributionBuckets(config: Config): Unit = {
    val bucketType = config.getString("bucket-type")
    histogramToDistributionConverter = bucketType match {
      case "exponential" =>
        new ExponentialBucket(
          numFiniteBuckets = config.getInt("num-finite-buckets"),
          growthFactor = config.getDouble("growth-factor"),
          scale = config.getDouble("scale")
        )
      case "linear" =>
        new LinearBucket(numFiniteBuckets = config.getInt("num-finite-buckets"), width = config.getDouble("width"), offset = config.getDouble("offset"))
      case _ =>
        throw new IllegalArgumentException(s"Unknown bucket type: $bucketType")
    }
  }

  private def configure(globalConfig: Config): Unit = {
    val config = globalConfig.getConfig(configPrefix)
    closeClient()

    configureDistributionBuckets(config.getConfig("metric.distribution"))
    readResource(config.getConfig("metric.resource"))

    val credentialsProvider = CredentialsProviderFactory.fromConfig(config)

    projectId = Option(config.getString("metric.google-project-id")).filter(_.nonEmpty).getOrElse(ServiceOptions.getDefaultProjectId())

    val settings = MetricServiceSettings.newBuilder()
    settings.setCredentialsProvider(credentialsProvider)

    client = MetricServiceClient.create(settings.build())
  }

  @SuppressWarnings(Array("NullAssignment"))
  private def closeClient(): Unit =
    Try {
      if (!(client eq null)) {
        client.close()
        client = null
      }
    }.failed.foreach { error =>
      logger.error("Failed to close MetricServiceClient", error)
    }

  def start(): Unit =
    configure(Kamon.config())

  def stop(): Unit =
    closeClient()

  def reconfigure(config: Config): Unit =
    configure(config)
}
