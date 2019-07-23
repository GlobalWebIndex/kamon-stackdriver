package kamon.stackdriver

import com.google.cloud.ServiceOptions
import com.google.cloud.trace.v2.{TraceServiceClient, TraceServiceSettings}
import com.google.devtools.cloudtrace.v2.Span.Attributes
import com.google.devtools.cloudtrace.v2._
import com.typesafe.config.Config
import kamon.trace.Span.{FinishedSpan, TagValue}
import kamon.util.CallingThreadExecutionContext
import kamon.{Kamon, SpanReporter}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.immutable.Set
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class StackdriverSpanReporter extends SpanReporter {
  private val logger = LoggerFactory.getLogger(getClass)

  private implicit def ec: ExecutionContext = CallingThreadExecutionContext

  private var projectId: String               = _
  private var client: TraceServiceClient      = _
  private var skipOperationNames: Set[String] = Set.empty

  def reportSpans(kamonSpans: Seq[FinishedSpan]): Unit = {
    val spans =
      kamonSpans.collect {
        case span if !skipOperationNames.contains(span.operationName) =>
          convertSpan(span)
      }
    if (spans.nonEmpty)
      writeSpans(spans)
  }

  private def configure(globalConfig: Config): Unit = {
    val config = globalConfig.getConfig(configPrefix)
    closeClient()

    projectId = Option(config.getString("span.google-project-id")).filter(_.nonEmpty).getOrElse(ServiceOptions.getDefaultProjectId)
    skipOperationNames = config.getStringList("span.skip-operation-names").asScala.toSet

    val credentialsProvider = CredentialsProviderFactory.fromConfig(config)

    val settings = TraceServiceSettings.newBuilder()
    settings.setCredentialsProvider(credentialsProvider)
    client = TraceServiceClient.create(settings.build())
  }

  @SuppressWarnings(Array("NullAssignment"))
  private def closeClient(): Unit =
    Try {
      if (!(client eq null)) {
        client.close()
        client = null
      }
    }.failed.foreach { error =>
      logger.error("Failed to close TraceServiceClient", error)
    }

  private def convertSpan(span: FinishedSpan): Span = span match {
    case FinishedSpan(context, operationName, start, end, tags, _) =>
      val traceId = context.traceID.string
      val spanId  = context.spanID.string
      val name    = SpanName.of(projectId, traceId, spanId).toString
      Span
        .newBuilder()
        .setName(name)
        .setDisplayName(TruncatableString.newBuilder().setValue(operationName).build())
        .setStartTime(instantToTimestamp(start))
        .setEndTime(instantToTimestamp(end))
        .setAttributes(Attributes.newBuilder().putAllAttributeMap(tagsToLabels(tags).asJava))
        .setSpanId(spanId)
        .setParentSpanId(context.parentID.string)
        .build()
  }

  private def writeSpans(spans: Seq[Span]): Unit = {
    val projectName = ProjectName.of(projectId).toString
    val request     = BatchWriteSpansRequest.newBuilder().setName(projectName).addAllSpans(spans.asJava).build()
    client.batchWriteSpansCallable().futureCall(request).onComplete {
      case Success(_) => // ok
      case Failure(e) => logger.error("Failed to upload traces", e)
    }
  }

  private def tagsToLabels(tags: Map[String, TagValue]): Map[String, AttributeValue] =
    tags.map {
      case (key, value: TagValue.Boolean) =>
        (key, AttributeValue.newBuilder().setBoolValue(value.text.toBoolean).build())
      case (key, value: TagValue.Number) =>
        (key, AttributeValue.newBuilder().setIntValue(value.number).build())
      case (key, value: TagValue.String) =>
        (key, AttributeValue.newBuilder().setStringValue(TruncatableString.newBuilder().setValue(value.string)).build())
    }

  def start(): Unit =
    configure(Kamon.config())

  def stop(): Unit =
    closeClient()

  def reconfigure(config: Config): Unit =
    configure(config)
}
