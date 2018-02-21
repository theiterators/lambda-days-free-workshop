package pl.iterators.forum.domain

import rapture.i18n.{En, I18n, Pl}

import scala.language.implicitConversions

package object i18n {
  type Langs = En with Pl
  implicit def i18nSyntax(i18n: I18n[String, Langs]): I18NOps = new I18NOps(i18n)
}
