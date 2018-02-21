package pl.iterators.forum.domain

import pl.iterators.forum.domain.i18n._
import java.util.Locale

import pl.iterators.forum.templates.html.SingleColumnEmail

case class EmailAddress(address: Email, name: Option[String] = None)
abstract class EmailMessage(val from: EmailAddress, val subject: String, val to: EmailAddress) {
  val `type`: String
  def text: String
  def html: String
}

trait Messages {
  def from: EmailAddress

  import EmailSubjects._
  import EmailPlainTexts._
  import EmailHtmlContent._
  import rapture.i18n.languages.en._

  case class ConfirmationMessage(newUser: Email, locale: Locale, confirmationLink: String)
      extends EmailMessage(from, confirmationSubject.fromLocale(locale), EmailAddress(newUser)) {
    override val `type` = "confirmation message"
    override def text   = confirmationText(confirmationLink).fromLocale(locale)
    override def html =
      SingleColumnEmail(
        title = ConfirmationEmail.title,
        header = ConfirmationEmail.header,
        htmlContent = ConfirmationEmail.content(confirmationLink),
        htmlFooter = ConfirmationEmail.footer,
        locale = locale
      ).body
  }
}
