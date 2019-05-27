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
 * This is an instance of an HTTP server for the google cloud hook that assumes we only ever have
 * exactly one conversation.
 *
 *  All pending "say" events are queued and spit out if we have a connection.
 *  All "read" events blocked until we have a result.
 *
 *  This class is highly mutable and wrong/bad.
 *
 * TODO - intatiate one of these PER CONNECTION, and run the app logic that way on an echo server.
 */
class BadTelnetServer(port: Int = 8022) extends CpsServer {
   private val server = new java.net.ServerSocket(port) 
   private var currentSocket: Option[Socket] = None

   private def ensureConnection[A](f: Socket => A): A = {
       currentSocket match {
           case Some(socket) => ()
           case None =>
             currentSocket = Some(server.accept)
       }
       f(currentSocket.get)
   }

   def onUserSpeech[R](continuation: String => R): Unit = ensureConnection { socket =>
       // TODO - read in socket.
       val in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream))
       continuation(in.readLine)
   }

   def say(msg: String): Unit = ensureConnection { socket =>
       // TODO - send the message out the socket.
       val out = new java.io.PrintWriter(socket.getOutputStream(), true)
       out.println(msg)
   }

   def close(): Unit = {
       currentSocket match {
           case Some(socket) => socket.close()
           case None => ()
       }
       // TODO - leave it open for another connection?
       server.close()
   }
}

/** Adapt the BadEchoServer into ZIO's algebra of effects/async. */
class ZioServer(s: BadTelnetServer) extends Conversation.Service[Any] {
      override def say(line: String): ZIO[Any, IOException, Unit] = {
        ZIO.effect(s.say(line)).refineOrDie {
          case e : IOException => e
        }
      }

      override def listen: ZIO[Any, IOException, String] = {
        ZIO.effectAsync[IOException, String] { callback =>
          s onUserSpeech { said =>
            callback(ZIO.succeed(said))
          }
        }
      }
}