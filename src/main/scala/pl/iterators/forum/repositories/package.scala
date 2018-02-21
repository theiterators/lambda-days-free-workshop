package pl.iterators.forum

import cats.free.Free

package object repositories {
  type AccountOperation[A] = Free[AccountRepository, A]
}
