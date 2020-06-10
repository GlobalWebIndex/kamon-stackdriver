package kamon

import java.time.Instant
import java.util.concurrent.Executor

import com.google.api.core.ApiFuture
import com.google.protobuf.Timestamp

import scala.concurrent.{Future, Promise}
import scala.util.Try

package object stackdriver {
  private[stackdriver] val configPrefix = "kamon.stackdriver"

  private[stackdriver] implicit def apiFutureToFuture[T](future: ApiFuture[T]): Future[T] = {
    val executor = new Executor {
      def execute(command: Runnable): Unit = command.run()
    }

    val promise = Promise[T]()
    future.addListener(
      new Runnable {
        def run(): Unit =
          promise.complete(Try(future.get()))
      },
      executor
    )
    promise.future
  }

  private[stackdriver] def instantToTimestamp(instant: Instant): Timestamp =
    Timestamp
      .newBuilder()
      .setSeconds(instant.getEpochSecond)
      .setNanos(instant.getNano)
      .build()
}
