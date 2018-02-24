package pl.iterators.forum

import java.time.Duration
import java.util.Locale

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import pl.iterators.forum.services.AuthenticationService
import pl.iterators.forum.utils.crypto.Crypto

import scala.concurrent.ExecutionContext
import scala.util.Try

case class HttpServerConfig(hostname: String, port: Int)
case class SmtpServerConfig(hostname: String, port: Int, username: String, password: String, sslOnConnect: Boolean, starttls: Boolean)
case class JwtConfig(ttl: Duration, secret: String)

trait Server extends Setup { self =>

  implicit def system: ActorSystem
  implicit def materializer: Materializer

  lazy val logger = Logging(system, "Main")

  val httpConfig       = config.getConfig("http")
  val httpServerConfig = HttpServerConfig(hostname = httpConfig.getString("host"), port = httpConfig.getInt("port"))

  val smtpConfig = config.getConfig("smtp")
  val smtpServerConfig = SmtpServerConfig(
    hostname = smtpConfig.getString("host"),
    port = smtpConfig.getInt("port"),
    username = smtpConfig.getString("username"),
    password = smtpConfig.getString("password"),
    sslOnConnect = Try(smtpConfig.getBoolean("ssl")).getOrElse(false),
    starttls = Try(smtpConfig.getBoolean("starttls")).getOrElse(false)
  )

  val jwtConfig = {
    val config              = appConfig.getConfig("jwt")
    val defaultTtlInMinutes = 30
    val defaultTtl          = Duration.ofMinutes(defaultTtlInMinutes)
    val minSecretLength     = 20
    val maxSecretLength     = 60

    val ttl    = Try(config.getDuration("ttl")) getOrElse defaultTtl
    val secret = Try(config.getString("secret")) getOrElse Crypto.generateStrongRandomAlphanumeric(minSecretLength, maxSecretLength)
    JwtConfig(ttl, secret)
  }
  val refreshTokenTtl       = appConfig.getDuration("refreshTokenTtl")
  val maxThreadsToFetch     = appConfig.getInt("maxThreadsToFetch")
  val maxPostsToFetch       = appConfig.getInt("maxPostsToFetch")
  val defaultThreadsToFetch = appConfig.getInt("defaultThreadsToFetch")
  val defaultPostsToFetch   = appConfig.getInt("defaultPostsToFetch")

  val confirmationLinkTemplate = mailingConfig.getString("confirmationLinkTemplate")
  val defaultLanguage          = Locale.forLanguageTag(mailingConfig.getString("defaultLanguage"))

  def authenticationService = new AuthenticationService {}

  lazy val restInterface = new RestInterface {
    override implicit val executor: ExecutionContext = self.executor

    override val jwtConfig       = self.jwtConfig
    override val defaultLanguage = self.defaultLanguage

    override val accountService               = self.accountService
    override val accountRepositoryInterpreter = self.accountRepository

    override val authenticationService = self.authenticationService

  }

}
