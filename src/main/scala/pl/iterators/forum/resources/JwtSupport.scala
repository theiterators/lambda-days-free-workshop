package pl.iterators.forum.resources

import java.time.Duration

import akka.event.LoggingAdapter
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.Credentials
import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pl.iterators.forum.domain.Claims
import play.api.libs.json._

import scala.util._

trait JwtSupport {
  import CommonJsonProtocol.claimsFormat
  import akka.http.scaladsl.server.Directives._

  private val defaultDuration: Duration          = Duration.ofMinutes(30)
  private val defaultAlgorithm: JwtHmacAlgorithm = JwtAlgorithm.HS512

  final def makeJwtClaim(issuer: String, claims: Claims, ttl: Duration): JwtClaim = {
    val claimContent = Json.toJson(claims)
    JwtClaim(Json.stringify(claimContent)).issuedNow.expiresIn(ttl.getSeconds).by(issuer)
  }

  final def encodeJwtToken(jwtClaim: JwtClaim, secret: String, algorithm: JwtHmacAlgorithm): String =
    JwtJson.encode(jwtClaim, secret, algorithm)

  final def encodeJwtToken(issuer: String,
                           secret: String,
                           claims: Claims,
                           ttl: Duration = defaultDuration,
                           algorithm: JwtHmacAlgorithm = defaultAlgorithm): String = {
    val jwtClaim = makeJwtClaim(issuer, claims, ttl)
    encodeJwtToken(jwtClaim, secret, algorithm)
  }

  def withJwtToken(secret: String, claims: Claims, ttl: Duration = defaultDuration, algorithm: JwtHmacAlgorithm = defaultAlgorithm) =
    extractHost flatMap { host =>
      val jwtClaim = makeJwtClaim(host, claims, ttl)
      val jwtToken = encodeJwtToken(jwtClaim, secret, algorithm)
      provide((jwtToken, jwtClaim))
    }

  def jwtAuthenticate(secret: String, algorithm: JwtHmacAlgorithm = defaultAlgorithm) = {
    def jwtAuthenticator(hostname: String, logger: LoggingAdapter)(credentials: Credentials): Option[JwtClaim] = credentials match {
      case Credentials.Missing => None
      case Credentials.Provided(token) =>
        JwtJson.decode(token, secret, Seq(algorithm)) match {
          case Success(claim) if claim.issuer.contains(hostname) => Some(claim)
          case Success(_) =>
            logger.warning("Rejected JWT token: wrong issuer")
            None
          case Failure(ex) =>
            logger.warning(s"Rejected JWT token (${ex.getMessage})")
            None
        }
    }

    extractHost flatMap { host =>
      extractLog flatMap { logger =>
        authenticateOAuth2(host, jwtAuthenticator(host, logger))
      }
    }
  }

  final def decodeClaims(jwtClaim: JwtClaim): Try[JsResult[Claims]] =
    Try(Json.parse(jwtClaim.content)).map(content => Json.fromJson[Claims](content))

  def withClaims(jwtClaim: JwtClaim): Directive1[Claims] = new Directive1[Claims]() {
    private def invalidRoute(msg: String) = extractLog { logger =>
      logger.warning(s"Invalid JWT token: $msg")
      reject(AuthorizationFailedRejection)
    }

    override def tapply(f: (Tuple1[Claims]) => Route) = decodeClaims(jwtClaim) match {
      case Failure(ex)              => invalidRoute(ex.getLocalizedMessage)
      case Success(JsError(_))      => invalidRoute("Cannot deserialize JWT token contents")
      case Success(JsSuccess(c, _)) => f(Tuple1(c))
    }
  }

}

object JwtSupport extends JwtSupport
