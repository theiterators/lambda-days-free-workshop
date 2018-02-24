package pl.iterators.forum.services

import pl.iterators.forum.domain._
import pl.iterators.forum.repositories._

import pl.iterators.forum.utils.free.syntax._

trait AuthenticationService {
  import AuthenticationService.AuthRequest

  type AuthenticationResponse = Either[AuthenticationError with Product with Serializable, Claims]

  def authenticate(authRequest: AuthRequest): AccountOperation[AuthenticationResponse] =
    AccountRepository
      .queryConfirmed(authRequest.email)
      .toEither(ifNone = InvalidCredentials)
      .subflatMap(subject => Either.cond(test = subject.canLogin, subject, if (subject.isBanned) Banned else InvalidCredentials))
      .subflatMap(subject => Either.cond(test = subject.validatePassword(authRequest.password), subject, InvalidCredentials))
      .map(_.claims)
      .value

}

object AuthenticationService {
  case class AuthRequest(email: Email, password: PasswordPlain)
}
