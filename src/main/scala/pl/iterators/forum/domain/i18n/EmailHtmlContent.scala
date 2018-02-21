package pl.iterators.forum.domain.i18n

import rapture.i18n._

object EmailHtmlContent {
  object ConfirmationEmail {
    val title                             = EmailSubjects.confirmationSubject
    val header                            = en"Welcome!" & pl"Witamy!"
    def content(confirmationLink: String) = I18n[En](contentEn(confirmationLink)) & I18n[Pl](contentPl(confirmationLink))
    val footer                            = I18n[En](footerEn) & I18n[Pl](footerPl)

    private def contentEn(confirmationLink: String) =
      s"""Hello,
         |<br><br>
         |
         |Thank your for signing up on Iterators Forum. In order to complete your registration, please click the link below.
         |<br><br>
         |
         |<a href="$confirmationLink">$confirmationLink</a>
         |<br><br>
         |
         |If you have problems, please paste the above URL into your web browser. This URL will only be valid for a limited time and will expire.
         |<br><br>
         |
         |P.S. If you received this email by mistake, simply delete it.
      """.stripMargin

    private def contentPl(confirmationLink: String) =
      s"""Witamy,
         |<br><br>
         |
         |Dziękujemy za zarejestrowanie się na Forum Iterators. W celu dokończenia rejestracji prosimy o kliknięcie w poniższy link:
         |<br><br>
         |
         |<a href="$confirmationLink">$confirmationLink</a>
         |<br><br>
         |
         |Jeśli masz problem z kliknięciem w link, możesz go wkleić do przeglądarki. Ten link będzie ważny tylko przez krótki okres czasu, a następnie wygaśnie.
         |<br><br>
         |
         |P.S. Jeśli otrzymałeś tego e-maila przez pomyłkę, po prostu go usuń.
         |<br><br>
      """.stripMargin

    private def footerEn =
      """<br><br>
        |Iterators Forum
        |<br><br>
      """.stripMargin

    private def footerPl =
      """<br><br>
        |Forum Iterators
        |<br><br>
      """.stripMargin
  }
}
