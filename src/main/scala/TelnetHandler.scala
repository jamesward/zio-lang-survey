import java.io.IOException
import java.net.{ServerSocket, Socket}

import ConversationRunner.ConversationEnv
import scalaz.zio.clock.Clock
import scalaz.zio.{ZIO, ZManaged}


object SimpleServerSockets {

  def listen(port: Int = 8022): ZIO[Any, IOException, ServerSocket] =
    ZIO.effect(new ServerSocket(port)) refineOrDie {
      case e: IOException => e
    }

  def accept(socket: ServerSocket): ZIO[Any, IOException, Socket] =
    ZIO.effect(socket.accept) refineOrDie {
        case e: IOException => e
    }

  def shutdown(socket: ServerSocket): ZIO[Any, Nothing, Unit] =
    ZIO.effectTotal(socket.close())
}

object SimpleSockets {
    def readLine(socket: Socket): ZIO[Any, IOException, String] =
      ZIO effect {
           val in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream))
           in.readLine
      } refineOrDie {
          case io: IOException => io
      }
    def println(socket: Socket, msg: String): ZIO[Any, IOException, Unit] =
      ZIO effect {
        val out = new java.io.PrintWriter(socket.getOutputStream(), true)
        out.println(msg)
      } refineOrDie {
        case io: IOException => io
      }
    def close(socket: Socket): ZIO[Any, Nothing, Unit] =
      ZIO.effectTotal(socket.close())
}

/** A mutable environment we use underneath ZIO to do all the trickery/hackery of running a terminal app. */
class SocketOutput(socket: Socket) extends Conversation.Output[Any] {

  private[this] var willPrompt: Boolean = false

  def isDone: Boolean = !willPrompt
  def say(value: String): ZIO[Any, IOException, Unit] =
    SimpleSockets.println(socket, value)
  def prompt(value: String): ZIO[Any, IOException, Unit] =
     for {
         _ <- ZIO.effectTotal({willPrompt = true})
         _ <- SimpleSockets.println(socket, value)
     } yield ()
}

object TelnetServerRunner {


    // handles a single converesation on a single socket.
    def handleConversation[Intent, State](socket: Socket, initialState: State,
                                          intentHandler: IntentHandler[Intent,State])(handler: Intent => ZIO[ConversationEnv, IOException, State]): ZIO[Monitoring with Clock, IOException, Unit] = {
      val initialIntent = intentHandler.fromRaw("", initialState)
      def runIntent(intent: Intent): ZIO[Monitoring with Clock, IOException, (Boolean, State)] = {
        for {
            outputSocket <- ZIO.succeed(new SocketOutput(socket))
            userState <- handler(intent).provideSome[Monitoring with Clock](services => new Conversation with Monitoring with Clock {
              override val clock: Clock.Service[Any] = services.clock
              override val output: Conversation.Output[Any] = outputSocket
              override val monitoring: Monitoring.Service[Any] = services.monitoring
            })
        } yield (outputSocket.isDone, userState)
      }
      def untilComplete(zio: ZIO[Monitoring with Clock, IOException, (Boolean, State)]): ZIO[Monitoring with Clock, IOException, Unit] = {
         zio.flatMap {
            case (true, s) => ZIO.succeed(())
            case (false, s) =>
              for {
                userInput <- SimpleSockets.readLine(socket)
                intent = intentHandler.fromRaw(userInput, s)
                result <- untilComplete(runIntent(intent))
              } yield result

         }
       }
      untilComplete(runIntent(initialIntent))
    }

    def openServer[Intent, State](port: Int = 8022, initialState: State,
                                  intentHandler: IntentHandler[Intent,State])(handler: Intent => ZIO[ConversationEnv, IOException, State]): ZIO[Monitoring with Clock, IOException, Unit] = {
       // Listen to a port
       ZManaged.make(SimpleServerSockets.listen(port))(SimpleServerSockets.shutdown).use { server =>
         // TODO - Figure out how to fork after accpeting and immediately allow more connections to be handled.
         val handleOneConnection =
           for {
               socket <- SimpleServerSockets.accept(server)
               forked <- handleConversation(socket, initialState, intentHandler)(handler).ensuring(SimpleSockets.close(socket)).fork
           } yield ()
         handleOneConnection.forever
       }
    }
}
