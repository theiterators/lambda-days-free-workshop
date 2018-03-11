package pl.iterators.forum.domain

sealed abstract class Error {
  val errorCode: String = this.getClass.getSimpleName.stripSuffix("$")
}

sealed trait ConfirmationError extends Error

sealed abstract class AccountError   extends Error
case object PasswordTooWeak          extends AccountError
sealed abstract class AccountDbError extends AccountError
case object EmailNotUnique           extends AccountDbError
case object NickNotUnique            extends AccountDbError with ConfirmationError
case object AccountNotExists         extends AccountDbError with ConfirmationError

sealed abstract class AuthenticationError extends Error
case object InvalidCredentials            extends AuthenticationError
case object Banned                        extends AuthenticationError

sealed abstract class TokenError extends ConfirmationError
case object InvalidToken         extends TokenError
case object TokenExpired         extends TokenError
