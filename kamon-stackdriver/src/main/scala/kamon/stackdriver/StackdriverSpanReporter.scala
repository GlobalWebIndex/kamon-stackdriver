package kamon.stackdriver

import com.google.cloud.ServiceOptions
import com.google.cloud.trace.v2.{TraceServiceClient, TraceServiceSettings}
import com.google.devtools.cloudtrace.v2.Span.Attributes
import com.google.devtools.cloudtrace.v2._
import com.typesafe.config.Config
import kamon.Kamon
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.tag.{Tag, TagSet}
import kamon.trace.Span.Finished
import kamon.util.CallingThreadExecutionContext
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
  private var projectName: String             = _
  private var skipOperationNames: Set[String] = Set.empty
  private var mappings: Map[String, String]   = Map.empty

  def reportSpans(kamonSpans: Seq[Finished]): Unit = {
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
    projectName = ProjectName.of(projectId).toString
    mappings = config.getObject("tags.mappings").unwrapped().asScala.mapValues(_.toString).toMap.withDefault(identity)

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

  private def convertSpan(span: Finished): Span = {
    val traceId = span.trace.id.string
    val spanId  = span.id.string
    val name    = SpanName.of(projectId, traceId, spanId).toString
    Span
      .newBuilder()
      .setName(name)
      .setDisplayName(TruncatableString.newBuilder().setValue(span.operationName).build())
      .setStartTime(instantToTimestamp(span.from))
      .setEndTime(instantToTimestamp(span.to))
      .setAttributes(Attributes.newBuilder().putAllAttributeMap(tagsToLabels(span.tags).asJava))
      .setSpanId(spanId)
      .setParentSpanId(span.parentId.string)
      .build()
  }

  private def writeSpans(spans: Seq[Span]): Unit = {
    val request = BatchWriteSpansRequest.newBuilder().setName(projectName).addAllSpans(spans.asJava).build()
    client.batchWriteSpansCallable().futureCall(request).onComplete {
      case Success(_) => // ok
      case Failure(e) => logger.error("Failed to upload traces", e)
    }
  }

  private def tagsToLabels(tags: TagSet): Map[String, AttributeValue] =
    tags
      .all()
      .map {
        case t: Tag.Boolean =>
          (mappings(t.key), AttributeValue.newBuilder().setBoolValue(t.value).build())
        case t: Tag.Long =>
          (mappings(t.key), AttributeValue.newBuilder().setIntValue(t.value).build())
        case t: Tag.String =>
          (mappings(t.key), AttributeValue.newBuilder().setStringValue(TruncatableString.newBuilder().setValue(t.value)).build())
      }
      .toMap

  def start(): Unit =
    configure(Kamon.config())

  def stop(): Unit =
    closeClient()

  def reconfigure(config: Config): Unit =
    configure(config)
}

object StackdriverSpanReporter {
  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new StackdriverSpanReporter()
  }
}
