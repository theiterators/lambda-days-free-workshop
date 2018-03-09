package pl.iterators.forum.repositories.interpreters

import akka.event.LoggingAdapter
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail}
import pl.iterators.forum.SmtpServerConfig
import pl.iterators.forum.domain.{EmailMessage, Ok}
import pl.iterators.forum.repositories.MailingRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MailingRepositorySmtpInterpreter(smtpServerConfig: SmtpServerConfig, logger: LoggingAdapter)(
    implicit executionContext: ExecutionContext)
    extends MailingRepositoryInterpreter {
  import MailingRepository._

  private def send(message: EmailMessage) = {
    val email = new HtmlEmail
    email.setHostName(smtpServerConfig.hostname)
    email.setSmtpPort(smtpServerConfig.port)
    email.setSslSmtpPort(smtpServerConfig.port.toString)
    email.setAuthenticator(new DefaultAuthenticator(smtpServerConfig.username, smtpServerConfig.password))
    email.setSSLOnConnect(smtpServerConfig.sslOnConnect)
    email.setStartTLSRequired(smtpServerConfig.starttls)
    email.setFrom(message.from.address, message.from.name.orNull)
    email.setSubject(message.subject)
    email.setTextMsg(message.text)
    email.setHtmlMsg(message.html)
    email.addTo(message.to.address, message.to.name.orNull)
    email.send()
  }

  def sendEmail(message: EmailMessage) = {
    val sendFuture = Future(send(message))

    val messageType = message.`type`
    val to          = message.to.address
    sendFuture.onComplete {
      case Success(_) => logger.info(s"Sent $messageType to <$to>")
      case Failure(t) => logger.error(t, s"Could not send $messageType to <$to>")
    }

    sendFuture map (_ => Ok)
  }

  override def apply[A](fa: MailingRepository[A]) = fa match {
    case SendEmail(message) => sendEmail(message)
  }
}
