package pl.iterators.forum

import com.typesafe.config.ConfigFactory
import pl.iterators.forum.domain.{Email, EmailAddress, Messages}
import pl.iterators.forum.domain.PasswordPolicies._
import pl.iterators.forum.repositories.interpreters._
import pl.iterators.forum.services.AccountService
import pl.iterators.forum.utils.db.PostgresDriver.api._

import scala.concurrent.ExecutionContext

trait Setup { self =>
  val config        = ConfigFactory.load()
  val appConfig     = config.getConfig("app")
  val mailingConfig = appConfig.getConfig("mailing")
  lazy val dbConfig = config.getConfig("db")

  lazy val db = Database.forConfig("db", config)

  val confirmationTokenTtl = appConfig.getDuration("confirmationTokenTtl")
  val passwordMinLength    = appConfig.getInt("passwordMinLength")
  val passwordPolicy: Validation = password =>
    for {
      _ <- mustBeOfLength(passwordMinLength)(password)
      _ <- mustContainLetter(password)
      _ <- mustContainUpper(password)
      _ <- mustContainDigit(password)
    } yield password

  def noReplyMessages = new Messages {
    private val noReplyConfig = mailingConfig.getConfig("no-reply")
    override val from         = EmailAddress(Email(noReplyConfig.getString("address")), Some(noReplyConfig.getString("name")))
  }

  implicit def executor: ExecutionContext

  def accountRepository: AccountRepositoryInterpreter = new AccountRepositoryDbInterpreter(db)
  def accountService = new AccountService {
    override val passwordPolicy = self.passwordPolicy
    override val messages       = self.noReplyMessages
  }

  def refreshTokenRepository: RefreshTokenRepositoryInterpreter           = new RefreshTokenRepositoryDbInterpreter(db)
  def confirmationTokenRepository: ConfirmationTokenRepositoryInterpreter = new ConfirmationTokenRepositoryDbInterpreter(db)

}
