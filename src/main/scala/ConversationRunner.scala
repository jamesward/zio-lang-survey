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

import scalaz.zio.clock.Clock
import scalaz.zio.{App, ZIO}

/** This trait helps adapt from the various underlying APIs for telnet/console/google actions SDK.
 * We attempt to unify all representations of user's intent into a single model.
 */
trait IntentHandler[Intent, State] {
    // TODO - actually pull this from Cloud API endpoints.
    def fromCloud(in: String): Intent
    // Because we're not providing NLU hooks into console simulation, we're doing all the work here...
    def fromRaw(in: String, state: State): Intent
}

object ConversationRunner {
  type ConversationEnv = Conversation with Monitoring with Clock
}
trait ConversationRunner extends App {
  type ConversationEnv = ConversationRunner.ConversationEnv
  type Intent
  type State

  /** Actually runs a single turn of conversation. */
  def handleConversationTurn(intent: Intent): ZIO[ConversationEnv, IOException, State]
  /** Maps intents from various environments into the user's domain. */
  def intentHandler: IntentHandler[Intent, State]
  def initialState: State



  override def run(args: List[String]): ZIO[Clock, Nothing, Int] = {
    val runner =
      if (args == List("-telnet")) telnetApp
      else consoleApp

    runner.fold(_ => 1, _ => 0)
  }

  def consoleApp: ZIO[Clock, IOException, Unit] = {
    // We opt for no-monitoring here.
    ConsoleHandler.runConversation(initialState, intentHandler, handleConversationTurn).provideSome[Clock] { services =>
       new Monitoring with Clock {
          override val clock: Clock.Service[Any] = services.clock
          override val monitoring: Monitoring.Service[Any] = Monitoring.NoMonitoring.monitoring
        }
    }
  }

  def telnetApp: ZIO[Clock, IOException, Unit] = {
    TelnetServerRunner.openServer(8022, initialState, intentHandler)(handleConversationTurn).provideSome[Clock] { services =>
       new Monitoring with Clock {
          override val clock: Clock.Service[Any] = services.clock
          override val monitoring: Monitoring.Service[Any] = Monitoring.NoMonitoring.monitoring
        }
    }
  }

}
