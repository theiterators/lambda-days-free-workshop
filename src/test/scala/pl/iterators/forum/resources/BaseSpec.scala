package pl.iterators.forum.resources

import java.time.Instant

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.{Id, ~>}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.scalatest._
import pdi.jwt._
import pl.iterators.forum.Server
import pl.iterators.forum.domain.Claims
import pl.iterators.forum.utils.directives._
import play.api.libs.json._

import scala.concurrent.Future
import scala.util._

abstract class BaseSpec
    extends FeatureSpec
    with Matchers
    with Directives
    with Server
    with PlayJsonSupport
    with CommonJsonProtocol
    with ScalatestRouteTest {
  import JwtSupport.{decodeClaims, encodeJwtToken}
  implicit val rejectionHandler: RejectionHandler = PlayJsonSupportRejectionHandler().seal
  implicit val exceptionHandler: ExceptionHandler = ExceptionHandlerWithIllegalArgumentException()

  protected def jwtValidateAndDecode(jsObject: JsObject): Claims = {
    val decoded = for {
      token     <- Try(jsObject.value("token").as[String])
      expiresAt <- Try(jsObject.value("expiresAt").as[Instant])
      jwtClaim  <- JwtJson.decode(token, jwtConfig.secret, Seq(JwtAlgorithm.HS512))
      if jwtClaim.expiration.contains(expiresAt.getEpochSecond)
      result <- decodeClaims(jwtClaim)
    } yield result

    decoded match {
      case Success(JsSuccess(claims, _)) => claims
      case Success(JsError(errors))      => fail(errors.mkString)
      case Failure(cause)                => fail(cause)
    }
  }

  protected def withJwtToken(request: HttpRequest, claims: Claims) =
    request.addCredentials(OAuth2BearerToken(encodeJwtToken("example.com", jwtConfig.secret, claims, jwtConfig.ttl)))

  protected def inFuture = new (Id ~> Future) {
    override def apply[A](fa: Id[A]): Future[A] = Future.successful(fa)
  }
}
