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
class StoredConversationOutput() extends Conversation.Output[Any] {
  var prompt: Boolean = false
  val spokenSentences = new collection.mutable.ArrayBuffer[String]() 
 
    /** Output the string to the user. */
    def say(value: String): ZIO[Any, IOException, Unit] = 
      ZIO effectTotal {
        spokenSentences += value
      }
    /** Prompt the user for more input. */
    // TODO - figure out how to specify intents we expect.
    def prompt(value: String): ZIO[Any, IOException, Unit] =
      ZIO effectTotal {
        spokenSentences += value
        prompt = true
      }
}

class MyAppSpec extends FlatSpec with Matchers with DefaultRuntime {

  def capturedConversationEnv(
    store: Conversation.Output[Any],
    monitoringService: Monitoring.Service[Any] = Monitoring.NoMonitoring.monitoring
  )(services: Clock): MyApp.ConversationEnv = {
    new  Conversation with Monitoring with Clock {
        override val clock: Clock.Service[Any] = services.clock
            override val scheduler: Scheduler.Service[Any] = services.scheduler
            override val output: Conversation.Output[Any] = store
            override val monitoring: Monitoring.Service[Any] = monitoringService
    }
  }

  // Business logic checks
  "The conversation" must "reject non-scala languages" in {
    val output = new StoredConversationOutput()
    
    val result = MyApp.handleConversationTurn(LanguageChoice("C#")).provideSome[Clock](
      capturedConversationEnv(output)
    )
    // Check output state.
    assert(unsafeRun(result) == Question())
  }
  "The conversation" must "accept scala languages" in {
    val output = new StoredConversationOutput()
    MyApp.handleConversationTurn(LanguageChoice("scala"))
    val result = MyApp.handleConversationTurn(LanguageChoice("Scala")).provideSome[Clock](
      capturedConversationEnv(output)
    )
    assert(unsafeRun(result) == Done())
  }

  // Check we monitor appropriately.
  "The conversation" must "record non-scala languages" in {
    val output = new StoredConversationOutput()
     val recorder = new RecordingMonitorService
     val result = MyApp.handleConversationTurn(LanguageChoice("C#")).provideSome[Clock](
       capturedConversationEnv(output, recorder)
     )
     assert(unsafeRun(result) == Question())
     assert(recorder.has("C#"))
     info("custom monitoring seems to work")
  }
}
