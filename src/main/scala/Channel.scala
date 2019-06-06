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

trait Channel extends Serializable {
  val channel: Channel.Service[Any]
}

object Channel extends Serializable {

  trait Service[R] {
    def in: ZIO[R, IOException, String]
    def out(s: String): ZIO[R, IOException, Unit]
  }

  object StdInOut extends Channel with Console.Live {
    override val channel: Service[Any] = new Service[Any] {
      override def in: ZIO[Any, IOException, String] = {
        console.getStrLn
      }

      override def out(s: String): ZIO[Any, IOException, Unit] = {
        console.putStrLn(s)
      }
    }
  }

  object channel extends Channel.Service[Channel] {
    override def out(s: String): ZIO[Channel, IOException, Unit] = ZIO.accessM[Channel](_.channel.out(s))
    override def in: ZIO[Channel, IOException, String] = ZIO.accessM[Channel](_.channel.in)
  }

}
