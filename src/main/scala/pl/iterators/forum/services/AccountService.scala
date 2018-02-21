package pl.iterators.forum.services

import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.AccountOperation

trait AccountService {
  def queryEmail(email: Email): AccountOperation[Option[AccountWithId]] = ???
  def lookup(id: AccountId): AccountOperation[Option[AccountWithId]]    = ???
}
