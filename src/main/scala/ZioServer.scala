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

 import scalaz.zio.ZIO
 import java.io.IOException
 import java.net.{Socket,ServerSocket}


trait CpsServer {
    /** Blocks the current thread until we get another spoken utterance and returns with the following.
     * This will be empty for initial query.
     */
    def onUserSpeech[R](continuation: String => R): Unit
    /** Speaks back to the user. */
    def say(msg: String): Unit
}

/**
 * This is an instance of an telnet server where we simply read/write conversational text.
 */
class CpsTelnetServer(socket: Socket) extends CpsServer {

   def onUserSpeech[R](continuation: String => R): Unit = {
       // TODO - read in socket.
       val in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream))
       continuation(in.readLine)
   }

   def say(msg: String): Unit = {
       // TODO - send the message out the socket.
       val out = new java.io.PrintWriter(socket.getOutputStream, true)
       out.println(msg)
   }

   def close(): Unit = {
       socket.close()
   }
}

/** Adapt the BadEchoServer into ZIO's algebra of effects/async. */
class ZioServer(val s: CpsTelnetServer) extends Conversation.Service[Any] {
      override def say(line: String): ZIO[Any, IOException, Unit] = {
        ZIO.effect(s.say(line)).refineOrDie {
          case e : IOException => e
        }
      }

      override def listen: ZIO[Any, IOException, String] = {
        ZIO.effectAsync[Any, IOException, String] { callback =>
          s onUserSpeech { said =>
            callback(ZIO.succeed(said))
          }
        }
      }
}
object ZioServer {

  def listen(port: Int = 8022): ZIO[Any, IOException, ServerSocket] =
    ZIO.effect(new ServerSocket(port)) refineOrDie {
      case e: IOException => e
    }

  def accept(socket: ServerSocket): ZIO[Any, Throwable, Socket] =
    ZIO.effect(socket.accept) refineOrDie {
        case e: IOException => e
    }

  def shutdown(socket: ServerSocket): ZIO[Any, Throwable, Unit] =
    ZIO.effect(socket.close()) refineOrDie {
         case e: IOException => e
     }
}
