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
import java.net.{ServerSocket, Socket}

import scalaz.zio.{App, Ref, Schedule, ZIO, ZManaged}
import Channel.channel._
import Monitoring.monitoring
import MyApp.myAppLogic
import org.http4s.server.blaze.BlazeServerBuilder
import scalaz.zio.clock.Clock
import scalaz.zio.scheduler.Scheduler

object MyApp extends App {
  type MyEnv = Conversation with Channel with Monitoring with Clock

  override def run(args: List[String]): ZIO[Clock, Nothing, Int] = {
    val runner =
      if (args == List("-telnet")) telnetServer
      else consoleApp

    runner.fold(_ => 1, _ => 0)
  }

  def consoleApp: ZIO[Clock, IOException, Unit] = {
    def withState(newState: Conversation.State = Conversation.Init): ZIO[Clock, IOException, Conversation.State] = {
      myAppLogic.provideSome[Clock] { clockService =>
        new Conversation with Channel with Monitoring with Clock {
          override val clock: Clock.Service[Any] = clockService.clock
          override val state: Conversation.State = newState
          override val channel: Channel.Service[Any] = Channel.StdInOut.channel
          override val monitoring: Monitoring.Service[Any] = Monitoring.NoMonitoring.monitoring
        }
      }
    }

    def untilComplete(zio: ZIO[Clock, IOException, Conversation.State]): ZIO[Clock, IOException, Conversation.State] = {
      zio.flatMap {
        case Conversation.Complete => ZIO.succeed(Conversation.Complete)
        case state => untilComplete(withState(state))
      }
    }

    untilComplete(withState()).unit
  }

  def telnetServer: ZIO[Clock, IOException, Unit] = {
    /*
    def acquire = ZioServer.listen()
    def release(server: ServerSocket) = ZIO.effectTotal(server.close())

    ZManaged.make(acquire)(release).use { server =>
      def conversation(client: Socket) = myAppLogic.provideSome[Clock] { clockService =>
        new Conversation with Monitoring with Clock {
          override val clock: Clock.Service[Any] = clockService.clock
          override val conversation: Conversation.Service[Any] = new ZioServer(new CpsTelnetServer(client))
          override val monitoring: Monitoring.Service[Any] = Monitoring.NoMonitoring.monitoring
        }
      }.ensuring(ZIO.effectTotal(client.close()))

      val haveConversation = for {
        client <- ZioServer.accept(server)
        result <- conversation(client)
      } yield result

      haveConversation.forever.mapError(new IOException(_))
    }
     */

    ???
  }

  val acceptableLanguages = Set("scala")

  def myAppLogic: ZIO[MyEnv, IOException, Conversation.State] = {

    def run(conversation: Conversation): ZIO[MyEnv, IOException, Conversation.State] = {
      conversation.state match {
        case Conversation.Init =>
          for {
            _ <- out("Survey says: What is the best programming language?")
            response <- in
          } yield Conversation.Response(response)

        case Conversation.Response(text) =>
          val output =
            if (acceptableLanguages(text.toLowerCase)) {
              out("Correct!")
            } else {
              out("Wrong.")
            }

          output.map(_ => Conversation.Complete)

        case Conversation.Complete =>
          ZIO.succeed(Conversation.Complete)
      }
    }

    for {
      conversation <- ZIO.environment[Conversation]
      next <- run(conversation)
    } yield next
  }

}
