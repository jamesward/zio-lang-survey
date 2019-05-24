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

import scalaz.zio.{App, Schedule, ZIO}
import Conversation.conversation._
import scalaz.zio.clock.Clock
import scalaz.zio.scheduler.Scheduler

object MyApp extends App {

  type MyEnv = Conversation with Clock

  override def run(args: List[String]): ZIO[Clock, Nothing, Int] = {
    myAppLogic.provideSome[Clock] { clockService =>
      new Conversation with Clock {
        override val clock: Clock.Service[Any] = clockService.clock
        override val scheduler: Scheduler.Service[Any] = clockService.scheduler
        override val conversation: Conversation.Service[Any] = Conversation.StdInOut.conversation
      }
    }.fold(_ => 1, _ => 0)
  }

  val myAppLogic: ZIO[MyEnv, IOException, Unit] = {
    def validate(lang: String) = {
      if (lang == "Scala") {
        say("Correct!")
      } else {
        for {
          _ <- say("Language not recognized, try again")
          _ <- ZIO.fail(new IOException("Language not recognized, try again"))
        } yield ()
      }
    }

    val listenUntilValid = for {
      lang <- listen
      _ <- validate(lang)
    } yield ()

    val takeSurvey: ZIO[MyEnv, IOException, Unit] = for {
      _ <- say("Survey says: What is the best programming language?")
      _ <- listenUntilValid.retry(Schedule.recurs(3)): ZIO[MyEnv, IOException, Unit] // otherwise this is ZIO[MyEnv, Any, Unit]
    } yield ()

    takeSurvey
  }

}
