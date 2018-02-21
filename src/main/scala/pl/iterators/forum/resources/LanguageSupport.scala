package pl.iterators.forum.resources

import java.util.Locale

import akka.http.scaladsl.model.headers.`Accept-Language`
import akka.http.scaladsl.model.headers.LanguageRange._

trait LanguageSupport {
  import akka.http.scaladsl.server.Directives.{provide, optionalHeaderValueByType}
  def defaultLanguage: Locale

  private def acceptLanguageToLocale(`accept-Language`: `Accept-Language`) = `accept-Language`.languages.maxBy(_.qValue()) match {
    case `*`(_)           => defaultLanguage
    case One(language, _) => Locale.forLanguageTag(language.toString())
  }
  def extractLocaleFromHeaders = optionalHeaderValueByType[`Accept-Language`](()) map (_.map(acceptLanguageToLocale))

  def determineLocale(maybePreferredLocale: Option[Locale] = None) =
    maybePreferredLocale.fold(extractLocaleFromHeaders.map(_.getOrElse(defaultLanguage)))(provide)
}
