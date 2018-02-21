package pl.iterators.forum.domain.i18n

import rapture.i18n._

object EmailPlainTexts {
  private def confirmationTextEn(confirmationLink: String) =
    s"""Hello,
       |
       |Thank you for signing up on Iterators Forum. In order to complete your registration , please click the link below:
       |
       |$confirmationLink
       |
       |This URL will be valid only for a limited time and will expire.
       |
       |P.S. If you received this email by mistake, simply delete it.
  """.stripMargin

  private def confirmationTextPl(confirmationLink: String) =
    s"""Witamy,
       |
       |Dziękujemy za zarejestrowanie się na Forum Iterators. W celu dokończenia rejestracji prosimy o kliknięcie w poniższy link:
       |
       |$confirmationLink
       |
       |Ten link będzie ważny tylko przez krótki okres czasu, a następnie wygaśnie.
       |
       |P.S. Jeśli otrzymałeś tego e-maila przez pomyłkę, po prostu go usuń.
  """.stripMargin

  def confirmationText(confirmationLink: String) =
    I18n[En](confirmationTextEn(confirmationLink)) &
      I18n[Pl](confirmationTextPl(confirmationLink))

}
