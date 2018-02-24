package pl.iterators.forum.services

import pl.iterators.forum.domain._
import pl.iterators.forum.repositories._

trait AuthenticationService {
  import AuthenticationService.AuthRequest

  type AuthenticationResponse = Either[AuthenticationError with Product with Serializable, Claims]

  def authenticate(authRequest: AuthRequest): AccountOperation[AuthenticationResponse] = ???

}

object AuthenticationService {
  case class AuthRequest(email: Email, password: PasswordPlain)
}
