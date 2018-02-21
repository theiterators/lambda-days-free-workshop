package pl.iterators.forum

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import akka.stream.ActorMaterializer
import pl.iterators.forum.utils.directives._

object Main extends Server {
  override implicit val system       = ActorSystem("forum-main", config)
  override implicit val executor     = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  implicit val rejectionHandler: RejectionHandler = PlayJsonSupportRejectionHandler().seal
  implicit val exceptionHandler: ExceptionHandler = ExceptionHandlerWithIllegalArgumentException()
  val routes                                      = restInterface.routes

  def main(args: Array[String]): Unit = {
    Http()
      .bindAndHandle(handler = routes, interface = httpServerConfig.hostname, port = httpServerConfig.port)
      .map { binding =>
        logger.info(s"HTTP server started at ${binding.localAddress}")
      }
      .recover { case ex => logger.error(ex, "Could not start HTTP server") }
  }
}
