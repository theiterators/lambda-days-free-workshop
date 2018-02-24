package pl.iterators.forum.services

import pl.iterators.forum.domain._
import pl.iterators.forum.repositories.{AccountOperation, AccountRepository}

trait AccountService {
  def queryEmail(email: Email): AccountOperation[Option[AccountWithId]] = AccountRepository.queryEmail(email)
  def lookup(id: AccountId): AccountOperation[Option[AccountWithId]]    = AccountRepository.lookup(id)
}
