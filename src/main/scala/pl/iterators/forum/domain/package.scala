package pl.iterators.forum

import java.nio.charset.StandardCharsets.UTF_8
import javax.mail.internet.InternetAddress

import org.jasypt.commons.CommonUtils
import pl.iterators.forum.domain.tags._
import pl.iterators.forum.utils.crypto.Crypto
import pl.iterators.forum.utils.db.WithId
import pl.iterators.forum.utils.tag._

import scala.Function.const
import scala.util.Try

package object domain {
  case object Ok

  type PasswordPlain = String @@ PasswordTag
  object PasswordPlain {
    object Valid {
      def apply(pass: String): Boolean = pass.nonEmpty
    }
    def apply(value: String): PasswordPlain =
      if (Valid(value)) value.@@[PasswordTag] else throw new IllegalArgumentException("password")
  }
  implicit class PasswordPlainOps(val self: PasswordPlain) extends AnyVal {
    def encrypt: Crypto.Password = Crypto.encryptFunction(self)
  }

  type Email = String @@ EmailTag
  object Email {
    object Valid {
      def apply(address: String): Boolean = Try(new InternetAddress(address).validate()).map(const(true)).getOrElse(false)
    }
    def apply(value: String)             = if (Valid(value)) value.@@[EmailTag] else throw new IllegalArgumentException("format")
    def uriDecode(string: String): Email = Email(new String(CommonUtils.fromHexadecimal(string), UTF_8))
    def uriEncode(email: Email): String  = CommonUtils.toHexadecimal(email.getBytes(UTF_8))
  }
  implicit class EmailOps(val self: Email) extends AnyVal {
    def uriEncode = Email.uriEncode(self)
  }

  type Nick = String @@ NickTag
  object Nick {
    object Valid {
      val allowedSymbols = Seq('-', '_', '!', '#', '$', '%', '^', '&', '*', '(', ')', '+', '=', '{', '}', '[', ']', '|', '\\')
      val minNickLength  = 3
      val maxNickLength  = 25
      private def correctFormat(nick: String) =
        nick.head.isLetterOrDigit && nick.last != ' ' && nick.tail.forall(ch =>
          ch.isLetterOrDigit || ch == ' ' || allowedSymbols.contains(ch))
      def apply(nick: String): Either[Error, Ok.type] = {
        val nickLength = nick.length
        if (nickLength < minNickLength) Left(TooShort)
        else if (nickLength > maxNickLength) Left(TooLong)
        else if (correctFormat(nick)) Right(Ok)
        else Left(FormatError)
      }
    }
    sealed abstract class Error
    case object TooLong     extends Error
    case object TooShort    extends Error
    case object FormatError extends Error

    def apply(value: String): Nick =
      Valid(value) match {
        case Right(_)          => value.@@[NickTag]
        case Left(FormatError) => throw new IllegalArgumentException("format")
        case Left(TooLong)     => throw new IllegalArgumentException("too long")
        case Left(TooShort)    => throw new IllegalArgumentException("too short")
      }
  }

  type PostContent = String @@ PostContentTag
  object PostContent {
    object Valid {
      val maxPostContentLength = 10000
      def apply(s: String): Either[Error, Ok.type] = {
        if (s.length > maxPostContentLength) Left(TooLong)
        else if (s.trim.isEmpty) Left(Empty)
        else Right(Ok)
      }
    }
    sealed abstract class Error
    case object TooLong extends Error
    case object Empty   extends Error

    def apply(value: String): PostContent =
      if (Valid(value).isRight) value.@@[PostContentTag] else throw new IllegalArgumentException("content")
  }

  type Subject = String @@ SubjectTag
  object Subject {
    object Valid {
      val maxSubjectLength = 160
      def apply(subject: String): Either[Error, Ok.type] = {
        val trimmed = subject.trim
        if (trimmed.length > maxSubjectLength) Left(TooLong)
        else if (trimmed.isEmpty) Left(Empty)
        else Right(Ok)
      }
    }
    sealed abstract class Error
    case object TooLong extends Error
    case object Empty   extends Error

    def apply(value: String): Subject =
      if (Valid(value).isRight) value.@@[SubjectTag] else throw new IllegalArgumentException("subject")
  }

  type AccountId = Int @@ IdTag[Account]
  object AccountId {
    def apply(id: Int): AccountId = id.@@[IdTag[Account]]
  }
  implicit class AccountIdOps(val self: AccountId) extends AnyVal {
    def asAuthorId = (self: Int).taggedWith[IdTag[Author]]
  }

  type RefreshToken      = Token[TokenType.Refresh]
  type ConfirmationToken = Token[TokenType.Confirmation]
  object RefreshToken {
    val TokenLength            = 128
    def generate(email: Email) = Token.generate[TokenType.Refresh](email, TokenLength)
  }
  object ConfirmationToken {
    val TokenLength            = 64
    def generate(email: Email) = Token.generate[TokenType.Confirmation](email, TokenLength)
  }

  type AuthorId = Int @@ IdTag[Author]
  object AuthorId {
    def apply(id: Int): AuthorId = id.@@[IdTag[Author]]
  }

  type PostId = Long @@ IdTag[Post[Any, Any]]
  object PostId {
    def apply(id: Long): PostId = id.@@[IdTag[Post[Any, Any]]]
  }

  type ThreadId = Long @@ IdTag[Thread[Any]]
  object ThreadId {
    def apply(id: Long): ThreadId = id.@@[IdTag[Thread[Any]]]
  }

  type AccountWithId = WithId[AccountId, Account]

  type ConfirmedAccountWithId = WithId[AccountId, ConfirmedAccount]
  implicit class ConfirmedAccountWithIdOps(val self: ConfirmedAccountWithId) extends AnyVal {
    def claims: Claims = Claims(id = self.id, email = self.email, nick = self.confirmedNick, isAdmin = self.isAdmin)
  }

  type AuthorWithId     = WithId[AuthorId, Author]
  type PostWithId[A, B] = WithId[PostId, Post[A, B]]
  type ThreadWithId[A]  = WithId[ThreadId, Thread[A]]

  type RefreshTokenId      = Long @@ IdTag[RefreshToken]
  type ConfirmationTokenId = Long @@ IdTag[ConfirmationToken]

}
