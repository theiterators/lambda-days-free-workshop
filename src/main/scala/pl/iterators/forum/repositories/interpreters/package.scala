package pl.iterators.forum.repositories

import cats.~>

import scala.concurrent.Future

package object interpreters {
  type AccountRepositoryInterpreter           = AccountRepository ~> Future
  type RefreshTokenRepositoryInterpreter      = RefreshTokenRepository ~> Future
  type ConfirmationTokenRepositoryInterpreter = ConfirmationTokenRepository ~> Future
  type MailingRepositoryInterpreter           = MailingRepository ~> Future
}
