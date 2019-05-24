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
import scalaz.zio.console.Console

trait Conversation extends Serializable {
  val conversation: Conversation.Service[Any]
}

object Conversation extends Serializable {
  trait Service[R] {
    def say(s: String): ZIO[R, IOException, Unit]
    def listen: ZIO[R, IOException, String]
  }

  object StdInOut extends Conversation with Console.Live {
    override val conversation: Service[Any] = new Service[Any] {
      override def say(s: String): ZIO[Any, IOException, Unit] = {
        console.putStrLn(s).mapError(_ => new IOException())
      }

      override def listen: ZIO[Any, IOException, String] = {
        console.getStrLn
      }
    }
  }

  object conversation extends Conversation.Service[Conversation] {
    override def say(s: String): ZIO[Conversation, IOException, Unit] = ZIO.accessM[Conversation](_.conversation.say(s))
    override def listen: ZIO[Conversation, IOException, String] = ZIO.accessM[Conversation](_.conversation.listen)
  }

}
