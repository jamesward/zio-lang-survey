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

import scalaz.zio.ZIO

trait Conversation extends Serializable {
  def output: Conversation.Output[Any]
}

object Conversation {
  trait Output[R] {
    /** Output the string to the user. */
    def say(value: String): ZIO[R, IOException, Unit]
    /** Prompt the user for more input. */
    // TODO - figure out how to specify intents we expect.
    def prompt(value: String): ZIO[Conversation, IOException, Unit]
  }

  object output extends Conversation.Output[Conversation] {
    def say(value: String): ZIO[Conversation, IOException, Unit] = ZIO.accessM[Conversation](_.output.say(value))
    def prompt(value: String): ZIO[Conversation, IOException, Unit] = ZIO.accessM[Conversation](_.output.prompt(value))
  }
}

