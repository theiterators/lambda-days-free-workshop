package pl.iterators.forum.repositories

import cats.~>

import scala.concurrent.Future

package object interpreters {
  type AccountRepositoryInterpreter = AccountRepository ~> Future
}
