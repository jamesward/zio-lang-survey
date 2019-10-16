/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException

import com.google.api.{Metric, MonitoredResource}
import com.google.cloud.monitoring.v3.MetricServiceClient
import com.google.monitoring.v3.{CreateTimeSeriesRequest, Point, ProjectName, TimeInterval, TimeSeries, TypedValue}
import com.google.protobuf.util.Timestamps
import zio.ZIO

trait Monitoring extends Serializable {
    val monitoring: Monitoring.Service[Any]
}
object Monitoring extends Serializable {
  trait Service[R] {
      /** A custom metric where we increment the number of times we see a language. */
      def languageVote(language: String): ZIO[R, IOException, Unit]
  }

  object NoMonitoring extends Monitoring {
    override val monitoring: Service[Any] = (language: String) => ZIO.succeed(())
  }

  /** This implementation provides a monitoring API using stackdriver. */
  class StackDriverMonitoring(client: MetricServiceClient, projectId: String) extends Monitoring {
    override val monitoring: Service[Any] = (language: String) => ZIO effect {
      val name = ProjectName.of(projectId)
      val labels = new java.util.HashMap[String, String]()
      labels.put("language", language)
      val metric = Metric.newBuilder()
        .setType("custom.googleapis.com/survey/daily_votes")
        .putAllLabels(labels)
        .build()
      val resourceLabels = new java.util.HashMap[String, String]
      resourceLabels.put("project_id", projectId)
      val resource = MonitoredResource.newBuilder.setType("global").putAllLabels(resourceLabels).build()
      val interval = TimeInterval.newBuilder()
        .setEndTime(Timestamps.fromMillis(System.currentTimeMillis()))
        .build()
      val point = Point.newBuilder.setInterval(interval).setValue(
        TypedValue.newBuilder.setDoubleValue(1).build()
      ).build()
      val ts =
        TimeSeries.newBuilder.setMetric(metric).setResource(resource).addPoints(point).build()
      val request =
        CreateTimeSeriesRequest.newBuilder.setName(name.toString).addTimeSeries(ts).build()
      client.createTimeSeries(request)
    } refineOrDie {
      case io: IOException => io
      case other: Throwable => new IOException("Failed to log language metric", other)
    }
  }

  object monitoring extends Monitoring.Service[Monitoring] {
    override def languageVote(language: String): ZIO[Monitoring, IOException, Unit] =
      ZIO.accessM[Monitoring](_.monitoring.languageVote(language))
  }
}
