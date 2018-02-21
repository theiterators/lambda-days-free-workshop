package pl.iterators.forum.domain.i18n

import java.util.Locale

import rapture.i18n.{DefaultLanguage, En, I18n, Pl}

final class I18NOps(val self: I18n[String, Langs]) extends AnyVal {
  def fromLocale(locale: Locale)(implicit defaultLanguage: DefaultLanguage[Langs]): String = locale.getLanguage match {
    case "en" => self[En]
    case "pl" => self[Pl]
    case _    => self
  }
}
