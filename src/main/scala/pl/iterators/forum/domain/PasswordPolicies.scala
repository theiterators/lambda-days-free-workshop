package pl.iterators.forum.domain

object PasswordPolicies {

  case object PasswordTooWeak
  type Validation = PasswordPlain => Either[PasswordTooWeak.type, String]

  val mustContainLetter: Validation = password => if (password.exists(_.isLetter)) Right(password) else Left(PasswordTooWeak)
  val mustContainUpper: Validation  = password => if (password.exists(_.isUpper)) Right(password) else Left(PasswordTooWeak)
  val mustContainDigit: Validation  = password => if (password.exists(_.isDigit)) Right(password) else Left(PasswordTooWeak)
  val mustContainNonAlphanumeric: Validation = password =>
    if (password.exists(!_.isLetterOrDigit)) Right(password) else Left(PasswordTooWeak)
  val mustBeOfLength: Int => Validation = minLength =>
    password => if (password.length >= minLength) Right(password) else Left(PasswordTooWeak)

}
