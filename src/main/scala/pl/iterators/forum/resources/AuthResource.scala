package pl.iterators.forum.resources

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import pl.iterators.forum.repositories.interpreters.AccountRepositoryInterpreter
import pl.iterators.forum.services.AuthenticationService
import pl.iterators.forum.services.AuthenticationService.AuthRequest
import play.api.libs.json._

object AuthResource {
  trait AuthProtocol extends CommonJsonProtocol {
    implicit val authRequestFormat: Format[AuthRequest] = Json.format[AuthRequest]
  }
}

trait AuthResource extends Resource with AuthResource.AuthProtocol {
  def authenticationService: AuthenticationService
  def accountRepositoryInterpreter: AccountRepositoryInterpreter

  import cats.instances.future._

  private def runAuthenticate(authRequest: AuthRequest) =
    authenticationService.authenticate(authRequest) foldMap accountRepositoryInterpreter

  protected val authenticate: Route = (post & entity(as[AuthRequest])) { authRequest =>
    onSuccess(runAuthenticate(authRequest)) {
      case Left(error)   => complete(Unauthorized -> error)
      case Right(claims) => completeWithJwtToken(claims)
    }
  }

  val authRoutes = (pathEndOrSingleSlash & respondWithHeaders(`Cache-Control`(`no-store`))) {
    authenticate
  }

}
