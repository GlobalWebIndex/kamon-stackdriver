package kamon.stackdriver

import java.util

import com.google.api.MetricDescriptor.MetricKind
import com.google.api.{Metric, MonitoredResource}
import com.google.cloud.ServiceOptions
import com.google.cloud.monitoring.v3.{MetricServiceClient, MetricServiceSettings}
import com.google.monitoring.v3._
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.MetricSnapshot.{Distributions, Values}
import kamon.metric.PeriodSnapshot
import kamon.module.{MetricReporter, Module, ModuleFactory}
import kamon.tag.{Tag, TagSet}
import kamon.util.CallingThreadExecutionContext
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class StackdriverMetricReporter extends MetricReporter {

  private val logger = LoggerFactory.getLogger(getClass)

  private val maxTimeseriesPerRequest = 100

  private implicit def ec: ExecutionContext = CallingThreadExecutionContext

  private[stackdriver] var client: MetricServiceClient = _

  private var projectId: String                                                  = _
  private var histogramToDistributionConverter: HistogramToDistributionConverter = _
  private var resource: MonitoredResource                                        = _

  private def projectName = ProjectName.format(projectId)

  start()

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val interval = TimeInterval
      .newBuilder()
      .setEndTime(instantToTimestamp(snapshot.from))
      .build()

    val histogramSeries = snapshot.histograms.flatMap(v => histogram(v, interval))
    val counterSeries   = snapshot.counters.flatMap(v => counters(v, interval))
    val gaugeSeries     = snapshot.gauges.flatMap(v => gauges(v, interval))
    val minMaxSeries    = snapshot.rangeSamplers.flatMap(v => histogram(v, interval))

    val allSeries: Seq[TimeSeries] = histogramSeries ++ counterSeries ++ gaugeSeries ++ minMaxSeries

    if (allSeries.nonEmpty) {
      val allSeriesSplit = allSeries.grouped(maxTimeseriesPerRequest)

      allSeriesSplit.foreach { series =>
        val request = CreateTimeSeriesRequest
          .newBuilder()
          .addAllTimeSeries(series.asJava)
          .setName(projectName)
          .build()
        writeSeries(request)
      }
    }
  }

  private[this] def buildResource(config: Config): MonitoredResource = {
    val resourceLabels = config
      .getConfig("labels")
      .entrySet()
      .asScala
      .map { entry =>
        entry.getKey -> entry.getValue.unwrapped().toString
      }
      .toMap

    MonitoredResource
      .newBuilder()
      .setType(config.getString("type"))
      .putAllLabels(resourceLabels.asJava)
      .build()
  }

  private[this] def writeSeries(timeSeriesRequest: CreateTimeSeriesRequest): Unit =
    client.createTimeSeriesCallable().futureCall(timeSeriesRequest).onComplete {
      case Success(_) => logger.trace("Time series created")
      case Failure(e) => logger.error("Failed to send TimeSeries", e)
    }

  private[this] val sanitizationRegexp = """[^\w]""".r

  private[this] def sanitizeTags(tags: TagSet): util.Map[String, String] = {
    val res = new util.HashMap[String, String]()
    tags.all.foreach {
      case t: Tag.Boolean =>
        res.put(sanitizationRegexp.replaceAllIn(t.key, "_"), t.value.toString)
      case t: Tag.Long =>
        res.put(sanitizationRegexp.replaceAllIn(t.key, "_"), t.value.toString)
      case t: Tag.String =>
        res.put(sanitizationRegexp.replaceAllIn(t.key, "_"), t.value)
    }
    res
  }

  private[this] def newTimeSeries(name: String, tags: TagSet, typedValue: TypedValue, timeInterval: TimeInterval) = {
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

  private[this] def histogram(v: Distributions, timeInterval: TimeInterval): Seq[TimeSeries] =
    v.instruments.map { i =>
      val distribution = histogramToDistributionConverter.histogramToDistribution(i.value.buckets, i.value.count)
      val typedValue = TypedValue
        .newBuilder()
        .setDistributionValue(distribution)
        .build()

      newTimeSeries(v.name, i.tags, typedValue, timeInterval)
    }

  private[this] def counters(v: Values[scala.Long], timeInterval: TimeInterval): Seq[TimeSeries] =
    v.instruments.map { i =>
      val typedValue = TypedValue
        .newBuilder()
        .setInt64Value(i.value)
        .build()

      newTimeSeries(v.name, i.tags, typedValue, timeInterval)
    }

  private[this] def gauges(v: Values[scala.Double], timeInterval: TimeInterval): Seq[TimeSeries] =
    v.instruments.map { i =>
      val typedValue = TypedValue
        .newBuilder()
        .setDoubleValue(i.value)
        .build()

      newTimeSeries(v.name, i.tags, typedValue, timeInterval)
    }

  private[this] def buildDistributionBuckets(config: Config): HistogramToDistributionConverter = {
    val bucketType = config.getString("bucket-type")
    bucketType match {
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

  private[this] def configure(globalConfig: Config): Unit = {
    val stackdriverConfig = globalConfig.getConfig(configPrefix)
    closeClient()

    histogramToDistributionConverter = buildDistributionBuckets(stackdriverConfig.getConfig("metric.distribution"))
    resource = buildResource(stackdriverConfig.getConfig("metric.resource"))
    projectId = Option(stackdriverConfig.getString("metric.google-project-id")).filter(_.nonEmpty).getOrElse(ServiceOptions.getDefaultProjectId)

    val credentialsProvider = CredentialsProviderFactory.fromConfig(stackdriverConfig)

    val settings = MetricServiceSettings
      .newBuilder()
      .setCredentialsProvider(credentialsProvider)

    client = MetricServiceClient.create(settings.build())
  }

  @SuppressWarnings(Array("NullAssignment"))
  private[this] def closeClient(): Unit =
    Try {
      if (!(client eq null)) {
        client.close()
        client = null
      }
    }.failed.foreach { error =>
      logger.error("Failed to close MetricServiceClient", error)
    }

  private[this] def start(): Unit =
    configure(Kamon.config())

  override def stop(): Unit =
    closeClient()

  override def reconfigure(config: Config): Unit =
    configure(config)
}

object StackdriverMetricReporter {
  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new StackdriverMetricReporter()
  }
}
