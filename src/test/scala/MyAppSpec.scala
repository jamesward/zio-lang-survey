/*
 * Copyright 2018 Google LLC.
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
import org.scalatest.{FlatSpec, Matchers}
import scalaz.zio.{ZIO, DefaultRuntime}
import scalaz.zio.clock.Clock
import scalaz.zio.scheduler.Scheduler

/** Helper module to remember all logged languages in the Monitoring component. */
class RecordingMonitorService extends Monitoring.Service[Any] {
  private val recorded = collection.mutable.Set[String]()
  override def languageVote(language: String): ZIO[Any, IOException, Unit] =
           ZIO effectTotal {
             recorded add language
           }
  def has(language: String): Boolean = recorded(language)          
}
/** Helper module to allow driving conversation by specifying only inputs. */
class StaticConversation(inputs: Seq[String]) extends Conversation.Service[Any] {
    // TODO - thread the index through here somehow, rather than hardcoding...
    var idx = 0
    override def say(s: String): ZIO[Any, IOException, Unit] = {
      ZIO.succeed(())
    }

    override def listen: ZIO[Any, IOException, String] = {
      ZIO.effect {
          if (idx < inputs.length) {
              val result = inputs(idx)
              idx += 1
              result
          } else throw new IOException("Conversation was terminated")
      } refineOrDie {
          case io: IOException => io
      }
    }
}

class MyAppSpec extends FlatSpec with Matchers with DefaultRuntime {
   "The conversation" must "record languages" in {
     val recorder = new RecordingMonitorService
     val staticConv = new StaticConversation(Seq("C#", "rust", "scala"))

     val result = MyApp.myAppLogic.provideSome[Clock]({ clockService =>
        new Conversation with Clock with Monitoring {
            override val clock: Clock.Service[Any] = clockService.clock
            override val scheduler: Scheduler.Service[Any] = clockService.scheduler
            override val conversation: Conversation.Service[Any] = staticConv
            override val monitoring: Monitoring.Service[Any] = recorder
        }
     }).fold(e => 1, a => 0)

     assert(unsafeRun(result) == 0)
     assert(recorder.has("scala"))
     assert(recorder.has("rust"))
     assert(recorder.has("C#"))
     info("custom monitoring seems to work")
  }
}
