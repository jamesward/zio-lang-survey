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
import Monitoring.monitoring
import Conversation.output
import org.http4s.server.blaze.BlazeServerBuilder
import scalaz.zio.clock.Clock
import scalaz.zio.scheduler.Scheduler

/** A mapping off all conversational intents we expect users to give us. */
sealed trait SurveyIntent
case class StartSurvey() extends SurveyIntent
case class LanguageChoice(language: String) extends SurveyIntent

/** The states our conversation could be in.  basically "starting" or "prompting". */
sealed trait SurveyState
case class Init() extends SurveyState
case class Question() extends SurveyState
case class Done() extends SurveyState


object MyApp extends ConversationRunner {

  val acceptableLanguages = Set("scala")    

  type State = SurveyState
  type Intent = SurveyIntent


  def initialState = Init()
  def handleConversationTurn(intent: SurveyIntent): ZIO[ConversationEnv, IOException, SurveyState] = {
     def askBestLanguage = 
       for {
         _ <- output.prompt("Survey says: What is the best programming language?")
       } yield Question()
     def handleLanguage(lang: String): ZIO[ConversationEnv, IOException, State] = {
       if (acceptableLanguages(lang.toLowerCase)) {
         output.say("Correct!").map(_ => Done())
       } else 
         for {
           _ <- output.say("Wrong.")
           result <- askBestLanguage
         } yield result
     }
     intent match {
       // The user started the survey.
       case StartSurvey() => askBestLanguage
       // The user answered the survey question.
       case LanguageChoice(lang) => 
          for {
            _ <- monitoring.languageVote(lang)
            result <- handleLanguage(lang)
          } yield result
       // We have no idea what the user just said.
       case _ => 
          for {
            _ <- output.say("I'm sorry, I don't understand.")
            result <- askBestLanguage
          } yield result 
     }
  }


  // Environment utilities.

  // Module to help us understand intents.
  override val intentHandler = new IntentHandler[SurveyIntent, SurveyState] {
    def fromCloud(in: String): SurveyIntent = ???
    def fromRaw(in: String, state: SurveyState): SurveyIntent = 
      state match {
        case Init() => StartSurvey()
        case Question() => LanguageChoice(in) 
        case Done() => ???
      }
  }

}
