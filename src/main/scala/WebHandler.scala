import java.io.IOException
import java.net.Socket
import java.nio.charset.Charset

import ConversationRunner.ConversationEnv
import org.apache.hc.core5.net.URLEncodedUtils
import rawhttp.core.body.EagerBodyReader
import rawhttp.core.{HttpVersion, RawHttp, RawHttpHeaders, RawHttpRequest, RawHttpResponse, StatusLine}
import scalaz.zio.clock.Clock
import scalaz.zio.{ZIO, ZManaged}


object WebSockets {
  private[this] val http = new RawHttp()

  def request(socket: Socket): ZIO[Any, IOException, RawHttpRequest] =
    ZIO effect {
      http.parseRequest(socket.getInputStream)
    } refineOrDie {
      case io: IOException => io
    }

  def response[A](socket: Socket, response: RawHttpResponse[A]): ZIO[Any, IOException, Unit] =
    ZIO effect {
      response.writeTo(socket.getOutputStream)
    } refineOrDie {
      case io: IOException => io
    }

  // even though socket.close can throw an IOException we pretend it won't cause ZManaged's release function needs to have an error of Nothing
  def close(socket: Socket): ZIO[Any, Nothing, Unit] =
    ZIO.effectTotal(socket.close())
}


/** A mutable environment we use underneath ZIO to do all the trickery/hackery of running a terminal app. */
class WebOutput(socket: Socket, inputParser: RawHttpRequest => Option[String]) extends Conversation.Output[Any] {
  var answer: Option[String] = None

  sealed trait Status

  object Status {
    case object OK extends Status
  }

  def statusToStatusLine(status: Status): StatusLine = {
    status match {
      case Status.OK => new StatusLine(HttpVersion.HTTP_1_1, 200, "OK")
    }
  }

  def response(request: RawHttpRequest, status: Status, body: String): RawHttpResponse[_] = {
    val bytes = body.getBytes
    val headers = RawHttpHeaders.newBuilder().`with`("Content-Type", "text/plain").`with`("Content-Length", bytes.length.toString).build()
    new RawHttpResponse(null, request, statusToStatusLine(status), headers, new EagerBodyReader(bytes))
  }

  override def say(value: String): ZIO[Any, IOException, Unit] = {
    for {
      req <- WebSockets.request(socket)
      _ <- ZIO.effectTotal({answer = inputParser(req)})
      res = response(req, Status.OK, value)
      _ <- WebSockets.response(socket, res)
    } yield ()
  }

  override def prompt(value: String): ZIO[Any, IOException, Unit] = {
    say(value)
  }
}

object WebServerRunner {

  def inputParser(req: RawHttpRequest): Option[String] = {
    import scala.collection.JavaConverters._
    URLEncodedUtils.parse(req.getUri, Charset.defaultCharset()).asScala.find(_.getName == "language").map(_.getValue)
  }

  def openServer[Intent, State](port: Int = 8080, initialState: State,
                                intentHandler: IntentHandler[Intent,State])(handler: Intent => ZIO[ConversationEnv, IOException, State]): ZIO[Monitoring with Clock, IOException, Unit] = {
     // Listen to a port
     ZManaged.make(SimpleServerSockets.listen(port))(SimpleServerSockets.shutdown).use { server =>

       def handleConnection(current: (Option[String], State)): ZIO[Monitoring with Clock, IOException, (Option[String], State)] = {
         val intent = intentHandler.fromRaw(current._1.getOrElse(""), current._2)

         ZManaged.make(SimpleServerSockets.accept(server))(SimpleSockets.close).use { socket =>
           for {
             outputSocket <- ZIO.succeed(new WebOutput(socket, inputParser))
             userState <- handler(intent).provideSome[Monitoring with Clock](services => new Conversation with Monitoring with Clock {
               override val clock: Clock.Service[Any] = services.clock
               override val output: Conversation.Output[Any] = outputSocket
               override val monitoring: Monitoring.Service[Any] = services.monitoring
             })
           } yield (outputSocket.answer, userState)
         }.flatMap(handleConnection)
       }

       handleConnection((None, initialState)).map(_ => ())
     }
  }
}
