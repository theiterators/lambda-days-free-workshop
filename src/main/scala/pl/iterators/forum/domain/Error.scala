package pl.iterators.forum.domain

sealed abstract class Error {
  val errorCode: String = this.getClass.getSimpleName.stripSuffix("$")
}

sealed abstract class AccountError   extends Error
case object PasswordTooWeak          extends AccountError
sealed abstract class AccountDbError extends AccountError
case object EmailNotUnique           extends AccountDbError
case object NickNotUnique            extends AccountDbError
case object AccountNotExists         extends AccountDbError
