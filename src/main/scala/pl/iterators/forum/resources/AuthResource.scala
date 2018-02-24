package pl.iterators.forum.resources

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import pl.iterators.forum.domain.Email
import pl.iterators.forum.repositories.interpreters.{AccountRepositoryInterpreter, RefreshTokenRepositoryInterpreter}
import pl.iterators.forum.services.AuthenticationService
import pl.iterators.forum.services.AuthenticationService.{AuthRequest, RefreshTokenRequest}
import play.api.libs.json._

object AuthResource {
  trait AuthProtocol extends CommonJsonProtocol {
    implicit val authRequestFormat: Format[AuthRequest]                 = Json.format[AuthRequest]
    implicit val refreshTokenRequestFormat: Format[RefreshTokenRequest] = Json.format[RefreshTokenRequest]
  }
}

trait AuthResource extends Resource with AuthResource.AuthProtocol {
  def authenticationService: AuthenticationService
  def accountRepositoryInterpreter: AccountRepositoryInterpreter
  def refreshTokenInterpreter: RefreshTokenRepositoryInterpreter

  import cats.instances.future._

  private def runAuthenticate(authRequest: AuthRequest) =
    authenticationService.authenticate(authRequest) foldMap accountRepositoryInterpreter

  protected val authenticate: Route = (post & entity(as[AuthRequest])) { authRequest =>
    onSuccess(runAuthenticate(authRequest)) {
      case Left(error)   => complete(Unauthorized -> error)
      case Right(claims) => completeWithJwtToken(claims)
    }
  }

  private def runObtainRefreshToken(email: Email) =
    authenticationService.obtainRefreshToken(email) foldMap (accountRepositoryInterpreter or refreshTokenInterpreter)

  protected lazy val obtainRefreshToken: Route = (post & requestEntityEmpty & extractClaims) { claims =>
    onSuccess(runObtainRefreshToken(claims.email)) {
      case Some(token) => complete(OK -> token)
      case None        => complete(NotFound)
    }
  }

  private def runRefreshClaims(refreshTokenRequest: RefreshTokenRequest) =
    authenticationService.refreshClaims(refreshTokenRequest) foldMap (accountRepositoryInterpreter or refreshTokenInterpreter)

  protected val refreshClaims: Route = (post & entity(as[RefreshTokenRequest])) { refreshTokenRequest =>
    onSuccess(runRefreshClaims(refreshTokenRequest)) {
      case Left(error)   => complete(BadRequest -> error)
      case Right(claims) => completeWithJwtToken(claims)
    }
  }

  val authRoutes = (pathEndOrSingleSlash & respondWithHeaders(`Cache-Control`(`no-store`))) {
    authenticate
  } ~ path("refresh-token") {
    obtainRefreshToken
  } ~ path("token") {
    refreshClaims
  }

}
