import java.io.IOException
import java.net.Socket

import ConversationRunner.ConversationEnv
import rawhttp.core.body.EagerBodyReader
import rawhttp.core.{HttpVersion, RawHttp, RawHttpHeaders, RawHttpRequest, RawHttpResponse, StatusLine}
import zio.clock.Clock
import zio.{ZIO, ZManaged}
import io.circe.Json
import rawhttp.core.errors.InvalidHttpRequest


object WebSockets {
  private[this] val http = new RawHttp()

  def request(socket: Socket): ZIO[Any, IOException, Option[RawHttpRequest]] =
    ZIO effect {
      Some(http.parseRequest(socket.getInputStream))
    } catchSome {
      case i: InvalidHttpRequest => ZIO.succeed(None)
    } refineOrDie {
      case io: IOException => io
    }

  def response[A](socket: Socket, response: RawHttpResponse[A]): ZIO[Any, IOException, Unit] =
    ZIO effect {
      response.writeTo(socket.getOutputStream)
    } refineOrDie {
      case io: IOException => io
    }

  def stringBody(req: RawHttpRequest): ZIO[Any, IOException, String] =
    ZIO effect {
      import java.nio.charset.Charset
      req.getBody.get.asRawString(Charset.defaultCharset)
    } refineOrDie {
      case io: IOException => io
    }

  def jsonBody(req: RawHttpRequest): ZIO[Any, IOException, Json] =
    for {
      raw <- stringBody(req)
      json <- ZIO.effect {
        io.circe.parser.parse(raw) match {
          case Left(err) => throw new IOException(s"Failed to parse incoming json: $err")
          case Right(json) => json
        }
      } refineOrDie {
        case io: IOException => io
      }
    } yield json

  // even though socket.close can throw an IOException we pretend it won't cause ZManaged's release function needs to have an error of Nothing
  def close(socket: Socket): ZIO[Any, Nothing, Unit] =
    ZIO.effectTotal(socket.close())
}


/** A mutable environment we use underneath ZIO to do all the trickery/hackery of running a terminal app. */
class WebOutput(socket: Socket, request: RawHttpRequest) extends Conversation.Output[Any] {
  // store pending JSON file to write out to the google actions SDK.
  private[this] var prompt: Boolean = false
  private[this] var answers = collection.mutable.ArrayBuffer[String]()

  sealed trait Status

  object Status {
    case object OK extends Status
  }

  def statusToStatusLine(status: Status): StatusLine = {
    status match {
      case Status.OK => new StatusLine(HttpVersion.HTTP_1_1, 200, "OK")
    }
  }

  def response(status: Status, body: String): RawHttpResponse[_] = {
    val bytes = body.getBytes
    val headers = RawHttpHeaders.newBuilder().`with`("Content-Type", "text/plain").`with`("Content-Length", bytes.length.toString).build()
    new RawHttpResponse(null, request, statusToStatusLine(status), headers, new EagerBodyReader(bytes))
  }

  override def say(value: String): ZIO[Any, IOException, Unit] = ZIO.effectTotal {
    answers += value
  }

  override def prompt(value: String): ZIO[Any, IOException, Unit] = {
    for {
      _ <- ZIO.effectTotal { prompt = true }
      _ <- say(value)
    } yield ()
  }

  // TODO - flush w/ state?
  def flush(): ZIO[Any, IOException, Unit] = {
    val text: String = answers.mkString(" ")
    val value: String = s"""{
          "payload": {
            "google": {
              "expectUserResponse": $prompt,
              "richResponse": {
                "items": [
                  {
                    "simpleResponse": {
                      "textToSpeech": "$text",
                      "displayText": "$text"
                    }
                  }
                ]
              }
            }
          }
        }"""
    WebSockets.response(socket, response(Status.OK, value))
  }

  def ok(): ZIO[Any, IOException, Unit] = {
    WebSockets.response(socket, response(Status.OK, ""))
  }

}

object WebServerRunner {

  def handleConversation[Intent, State](socket: Socket, initialState: State,
                                        intentHandler: IntentHandler[Intent,State])(handler: Intent => ZIO[ConversationEnv, IOException, State]): ZIO[Monitoring with Clock, IOException, Unit] = {

    def doJson(req: RawHttpRequest) = {
      for {
        outputSocket <- ZIO.succeed(new WebOutput(socket, req))
        json <- WebSockets.jsonBody(req)
        intent = intentHandler.fromCloud(json)
        // TODO - pull state from webhook/context in dialog-flow app.
        userState <- handler(intent).provideSome[Monitoring with Clock](services => new Conversation with Monitoring with Clock {
          override val clock: Clock.Service[Any] = services.clock
          override val output: Conversation.Output[Any] = outputSocket
          override val monitoring: Monitoring.Service[Any] = services.monitoring
        })
        // TODO write userState out in context in dialog-flow app.
        _ <- outputSocket.flush()
      } yield ()
    }

    def doOk(req: RawHttpRequest) = {
      for {
        outputSocket <- ZIO.succeed(new WebOutput(socket, req))
        _ <- outputSocket.ok()
      } yield ()
    }

    for {
      maybeReq <- WebSockets.request(socket)
      _ <- maybeReq.fold[ZIO[Monitoring with Clock, IOException, Unit]](ZIO.unit) { req =>
        if (req.getMethod == "GET") doOk(req) else doJson(req)
      }
    } yield ()
  }

  def openServer[Intent, State](port: Int = 8080, initialState: State,
                                intentHandler: IntentHandler[Intent,State])(handler: Intent => ZIO[ConversationEnv, IOException, State]): ZIO[Monitoring with Clock, IOException, Unit] = {
     // Listen to a port
     ZManaged.make(SimpleServerSockets.listen(port))(SimpleServerSockets.shutdown).use { server =>
      val handleOneConnection =
        for {
          socket <- SimpleServerSockets.accept(server)
          _ <- handleConversation(socket,initialState, intentHandler)(handler).ensuring(SimpleSockets.close(socket)).fork
        } yield ()
       handleOneConnection.forever
     }
  }
}
