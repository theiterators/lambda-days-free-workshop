package pl.iterators.forum.fixtures

import java.util.Locale

import cats.data.Writer
import cats.{Id, ~>}
import pl.iterators.forum.domain.{ConfirmationToken, Email, EmailMessage, Ok}
import pl.iterators.forum.repositories.MailingRepository
import pl.iterators.forum.repositories.MailingRepository.SendEmail
import pl.iterators.forum.services.AccountService.ConfirmationEmailEnv

trait MailingFixture {
  final val nullInterpreter: MailingRepository ~> Id = λ[MailingRepository ~> Id] {
    case SendEmail(_) => Ok
  }
  final val confirmationEmailEnv = new ConfirmationEmailEnv {
    override def confirmationLink(email: Email, token: ConfirmationToken) =
      s"http://example.com/registration?token=$token&email=${Email.uriEncode(email)}"
    override val locale = Locale.US
  }

  import cats.instances.list._
  type EmailLog[V] = Writer[List[EmailMessage], V]

  protected def writeEmailLogValue: Id ~> EmailLog = λ[Id ~> EmailLog](Writer.value(_))
  def mailingLogger: MailingRepository ~> EmailLog =
    λ[MailingRepository ~> EmailLog] {
      case SendEmail(message) => Writer(List(message), Ok)
    }
}
