package pl.iterators.forum.resources

import java.time._

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{StatusCode, Uri}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import pl.iterators.forum.JwtConfig
import pl.iterators.forum.domain.{AccountId, Claims, Email, Nick}
import pl.iterators.forum.utils.db.WithId
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait Resource extends Directives with PlayJsonSupport with JwtSupport with CommonJsonProtocol {
  implicit def executor: ExecutionContext
  def jwtConfig: JwtConfig

  private val apiPath         = Path / "api"
  final def baseUri(uri: Uri) = uri.withPath(apiPath).withQuery(Query.Empty)
  final val withBaseUri       = extractUri.flatMap(uri => provide(baseUri(uri)))

  private def resourceLocation[Id](baseUri: Uri, path: Id => Path): Id => Uri =
    path andThen (resourcePath => baseUri.withPath(baseUri.path ++ Path.Slash(resourcePath)))

  def completeWithLocation[Id: Writes, T: OWrites](resource: WithId[Id, T])(path: Id => Path): Route =
    withBaseUri { baseUri =>
      val locationFunction = resourceLocation(baseUri, path)
      val location         = locationFunction(resource.id)
      respondWithHeader(Location(location)) {
        implicit val writes: OWrites[WithId[Id, T]] = resourceWrites(locationFunction)
        complete(Created -> resource)
      }
    }

  def completeWithJwtToken(claims: Claims) = withJwtToken(jwtConfig.secret, claims, jwtConfig.ttl) {
    case (jwtToken, jwtClaim) =>
      val expiresAt = jwtClaim.expiration.fold(Instant.now())(expiration => Instant.ofEpochSecond(expiration))
      complete(OK -> JsObject(Seq("token" -> JsString(jwtToken), "expiresAt" -> JsString(expiresAt.toString))))
  }

  def completeAsResource[Id: Writes, T: OWrites](resource: WithId[Id, T], status: StatusCode = OK)(path: Id => Path) = withBaseUri {
    baseUri =>
      implicit val writes: OWrites[WithId[Id, T]] = resourceWrites(resourceLocation(baseUri, path))
      complete(status -> resource)
  }

  def jwtAuthorize(check: Claims => Boolean = authAny) = extractClaims flatMap { claims =>
    authorize(check(claims))
  }

  final def extractClaims = jwtAuthenticate(jwtConfig.secret) flatMap { jwtClaim =>
    withClaims(jwtClaim)
  }

  final def authSingleAccount(accountId: AccountId): Claims => Boolean = _.id == accountId
  final def authSingleAccount(email: Email): Claims => Boolean         = _.email == email
  final val authAdmin: Claims => Boolean                               = _.isAdmin
  final val authAny: Claims => Boolean                                 = Function.const(true)

  implicit class CheckFunctionCombinator(self: Claims => Boolean) {
    def |(other: Claims => Boolean): Claims => Boolean      = claims => self(claims) || other(claims)
    def except(other: Claims => Boolean): Claims => Boolean = claims => !other(claims) && self(claims)
  }

  protected implicit val emailUnmarshaller: Unmarshaller[String, Email] = Unmarshaller(
    (_: ExecutionContext) => (email: String) => Future.fromTry(Try(Email.uriDecode(email))))

  protected implicit val nickUnmarshaller: Unmarshaller[String, Nick] = Unmarshaller(
    (_: ExecutionContext) => (nick: String) => Future.fromTry(Try(Nick(nick))))
}
