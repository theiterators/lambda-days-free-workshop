package pl.iterators.forum.domain

import java.time.OffsetDateTime

import pl.iterators.forum.utils.crypto.Crypto.Password
import pl.iterators.forum.utils.db.WithId

case class Claims(id: AccountId, email: Email, nick: Nick, isAdmin: Boolean) {
  def toMyAccountWithId                          = WithId(id, MyAccount(email, nick, isAdmin))
  def canUpdatePost(post: Post[AuthorWithId, _]) = isAdmin || post.author.id == id
}

trait Account {
  def email: Email
  def nick: Option[Nick]
  def about: Option[String]
  def encryptedPassword: Password
  def createdAt: OffsetDateTime
  def isAdmin: Boolean
  def isBanned: Boolean
  def isConfirmed: Boolean

  final def validatePassword(password: String): Boolean =
    password.nonEmpty && encryptedPassword.verify(password)

  final def canLogin: Boolean = !isBanned && isConfirmed

  def withPassword(newPassword: Password): Account
  def withAbout(newAbout: Option[String]): Account
}

trait ConfirmedAccount extends Account {
  def confirmedNick: Nick

  override final def nick        = Some(confirmedNick)
  override final val isConfirmed = true
}

case class MyAccount(email: Email, nick: String, isAdmin: Boolean)
