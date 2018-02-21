package pl.iterators.forum.utils.db

import pl.iterators.forum.utils.tag._

import scala.annotation.implicitNotFound
import scala.language.implicitConversions

case class WithId[+Id, +Model](id: Id, model: Model) {
  def transform[A](f: Model => A): WithId[Id, A]   = copy(model = f(model))
  def transformId[A](f: Id => A): WithId[A, Model] = copy(id = f(id))
}
object WithId {
  def tupled[Id, Model] = (apply[Id, Model] _).tupled

  def New[Id] = new WithIdPartiallyApplied[Id]

  class WithIdPartiallyApplied[Id] {
    def apply[Model](model: Model)(implicit fakeId: FakeId[Id])            = WithId(fakeId.id, model)
    def apply[Model](models: Iterable[Model])(implicit fakeId: FakeId[Id]) = models.iterator.map(WithId(fakeId.id, _)).toIterable
  }

  @implicitNotFound("Can't create new element with id type ${T}")
  sealed abstract class FakeId[T] { val id: T }
  trait LowPriorityImplicits {
    implicit def taggedId[T, U](implicit ev: FakeId[T]): FakeId[T @@ U] = new FakeId[T @@ U] { val id = ev.id.taggedWith[U] }
  }
  object FakeId extends LowPriorityImplicits {
    implicit object IntFakeId  extends FakeId[Int]  { val id = -1  }
    implicit object LongFakeId extends FakeId[Long] { val id = -1L }
  }

  implicit def toModel[Id, Model](modelWithId: WithId[Id, Model]): Model = modelWithId.model
}
