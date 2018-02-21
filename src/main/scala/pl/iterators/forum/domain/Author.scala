package pl.iterators.forum.domain

import java.time.OffsetDateTime

trait Author {
  def nick: Nick
  def memberSince: OffsetDateTime
  def numberOfPosts: Int
}
