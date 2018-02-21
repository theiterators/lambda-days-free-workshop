package pl.iterators.forum.repositories.interpreters

import pl.iterators.forum.domain.tags._
import pl.iterators.forum.utils.crypto._
import pl.iterators.forum.utils.db.PostgresDriver.api._
import pl.iterators.forum.utils.tag._
import slick.lifted.StringColumnExtensionMethods

import scala.language.implicitConversions
import scala.reflect.ClassTag

trait TypeMappers extends Crypto.TypeMappers {
  def taggedColumnType[T, U](implicit tColumnType: BaseColumnType[T], clsTag: ClassTag[T @@ U]): BaseColumnType[T @@ U] =
    MappedColumnType.base[T @@ U, T](identity, _.@@[U])
  def mappedTaggedColumnType[T, U](f: T => T)(implicit tColumnType: BaseColumnType[T], clsTag: ClassTag[T @@ U]): BaseColumnType[T @@ U] =
    MappedColumnType.base[T @@ U, T](identity, t => f(t).@@[U])

  implicit val nickColumnType: BaseColumnType[String @@ NickTag]               = taggedColumnType[String, NickTag]
  implicit val emailColumnType: BaseColumnType[String @@ EmailTag]             = taggedColumnType[String, EmailTag]
  implicit val postContentColumnType: BaseColumnType[String @@ PostContentTag] = taggedColumnType[String, PostContentTag]
  implicit val subjectColumnType: BaseColumnType[String @@ SubjectTag]         = mappedTaggedColumnType[String, SubjectTag](_.trim)

  private val anyIntIdColumnType: BaseColumnType[Int @@ IdTag[Any]] = taggedColumnType[Int, IdTag[Any]]
  implicit def intIdColumnType[A]: BaseColumnType[Int @@ IdTag[A]]  = anyIntIdColumnType.asInstanceOf[BaseColumnType[Int @@ IdTag[A]]]

  private val anyLongIdColumnType: BaseColumnType[Long @@ IdTag[Any]] = taggedColumnType[Long, IdTag[Any]]
  implicit def longIdColumnType[A]: BaseColumnType[Long @@ IdTag[A]]  = anyLongIdColumnType.asInstanceOf[BaseColumnType[Long @@ IdTag[A]]]

  implicit def taggedStringExtensionMethods[T](taggedString: Rep[String @@ T]): StringColumnExtensionMethods[String @@ T] =
    new StringColumnExtensionMethods(taggedString)
  implicit def taggedStringOptionExtensionMethods[T](
      taggedStringOpt: Rep[Option[String @@ T]]): StringColumnExtensionMethods[Option[String @@ T]] =
    new StringColumnExtensionMethods(taggedStringOpt)
}

object TypeMappers extends TypeMappers
