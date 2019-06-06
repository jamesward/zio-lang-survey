import java.io.IOException

import ConversationRunner.ConversationEnv
import scalaz.zio.ZIO
import scalaz.zio.clock.Clock

/** A mutable environment we use underneath ZIO to do all the trickery/hackery of running a terminal app. */
class ConsoleOutput() extends Conversation.Output[Any] {
  private[this] var willPrompt: Boolean = false

  def isDone: Boolean = !willPrompt
  def say(value: String): ZIO[Any, IOException, Unit] =
    ZIO.effect(Console.println(value)) refineOrDie {
      case io: IOException => io
    }
  def prompt(value: String): ZIO[Any, IOException, Unit] =
     ZIO effect {
       willPrompt = true
       Console.println(value)
     } refineOrDie {
       case io: IOException => io
     }
}

sealed trait ConsoleState[State]
case class ConsoleStart[State](state: State) extends ConsoleState[State]
case class ConsolePrompt[State](state: State) extends ConsoleState[State]
case class ConsoleDone[State]() extends ConsoleState[State]

/** Helper runners for ZIO magic on Console... */
object ConsoleHandler {
    def runTurn[Intent, State](
        state: ConsoleState[State],
        intentHandler: IntentHandler[Intent, State],
        handler: Intent => ZIO[ConversationEnv, IOException, State]): ZIO[Monitoring with Clock, IOException, ConsoleState[State]] = {
      val intent =
        state match {
            case ConsoleStart(state) => ZIO.succeed(intentHandler.fromRaw("", state))
            case ConsoleDone() => ZIO.fail(new IOException("Conversation terminated, cannot prompt."))
            case ConsolePrompt(state) => ZIO.effect(intentHandler.fromRaw(Console.readLine, state)).refineOrDie {
                case io: IOException => io
            }
        }
      for {
          i <- intent
          outputHack = new ConsoleOutput()
          userState <- handler(i).provideSome[Monitoring with Clock] { services =>
            new Conversation with Monitoring with Clock {
              override val clock: Clock.Service[Any] = services.clock
              override val output: Conversation.Output[Any] = outputHack
              override val monitoring: Monitoring.Service[Any] = services.monitoring
            }
          }
      } yield
        if(outputHack.isDone) ConsoleDone()
        else ConsolePrompt(userState)
    }

    def runConversation[Intent, State](
        initialState: State,
        intentHandler: IntentHandler[Intent, State],
        handler: Intent => ZIO[Monitoring with Clock with Conversation, IOException, State]): ZIO[Monitoring with Clock, IOException, Unit] = {

       def untilComplete(zio: ZIO[Monitoring with Clock, IOException, ConsoleState[State]]): ZIO[Monitoring with Clock, IOException, ConsoleState[State]] = {
         zio.flatMap {
            case ConsoleDone() => ZIO.succeed(ConsoleDone())
            case state => untilComplete(runTurn(state, intentHandler, handler))
         }
       }
       untilComplete(ZIO.succeed(ConsoleStart(initialState))).unit
    }

}
