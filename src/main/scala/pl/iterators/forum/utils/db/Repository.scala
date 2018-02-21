package pl.iterators.forum.utils.db

import org.postgresql.util.PSQLException
import pl.iterators.forum.utils.db.PostgresDriver.api._
import pl.iterators.forum.utils.db.WithId.FakeId
import slick.ast.BaseTypedType
import slick.dbio.Effect._

import scala.concurrent.ExecutionContext
import scala.util._

class ReadOnlyRepository[IdType: BaseTypedType, Model, TableType <: TableWithId[IdType, Model]](val table: TableQuery[TableType]) {
  type Entity = WithId[IdType, Model]

  final def filterId(id: Rep[IdType]) = table.filter(_.id === id)
  final val filterIdCompiled          = Compiled(filterId _)

  final def find(id: IdType)         = filterIdCompiled(id).result.headOption
  final def findExisting(id: IdType) = filterIdCompiled(id).result.head

  protected final def id = table.map(_.id)

  def count: DBIOAction[Int, NoStream, Read] = table.length.result

  final def isEmpty(implicit ec: ExecutionContext): DBIOAction[Boolean, NoStream, Read] = count map (_ == 0)

  final def nonEmpty(implicit ec: ExecutionContext): DBIOAction[Boolean, NoStream, Read] = count map (_ > 0)

  def exists(id: IdType): DBIOAction[Boolean, NoStream, Read] = filterId(id).exists.result

  final def exists(entity: Entity): DBIOAction[Boolean, NoStream, Read] = exists(entity.id)
}

class Repository[IdType: BaseTypedType, Model, TableType <: TableWithId[IdType, Model]](table: TableQuery[TableType])
    extends ReadOnlyRepository[IdType, Model, TableType](table) {
  import pl.iterators.forum.utils.db.Repository._

  type UpdateResult        = Either[NotUpdated.type, Entity]
  type PartialUpdateResult = Either[PartialUpdateResults, Entity]

  def update(entity: Entity)(implicit ec: ExecutionContext): DBIOAction[UpdateResult, NoStream, Write] =
    filterIdCompiled(entity.id) update entity map (updated => if (updated == 1) Right(entity) else Left(NotUpdated))

  final def update(id: IdType, f: Model => Model)(implicit ec: ExecutionContext): DBIOAction[UpdateResult, NoStream, Read with Write] =
    find(id).flatMap {
      case None      => DBIO.successful(Left(NotUpdated))
      case Some(row) => update(row.transform(f))
    }

  final def updatePartially(id: IdType, pf: PartialFunction[Model, Model])(
      implicit ec: ExecutionContext): DBIOAction[PartialUpdateResult, NoStream, Read with Write] =
    find(id).flatMap {
      case None      => DBIO.successful(Left(NotUpdated))
      case Some(row) => if (pf.isDefinedAt(row)) update(row.transform(pf)) else DBIO.successful(Left(NotDefined))
    }

  final def tryUpdate(id: IdType, f: Model => Model)(
      implicit ec: ExecutionContext): DBIOAction[Try[UpdateResult], NoStream, Read with Write] = mapPSQLException(update(id, f))

  def insert(model: Model)(implicit ev: FakeId[IdType]): DBIOAction[IdType, NoStream, Write] =
    (table returning id) += WithId.New[IdType](model)

  final def tryInsert(model: Model)(implicit ev: FakeId[IdType], ec: ExecutionContext): DBIOAction[Try[IdType], NoStream, Write] =
    mapPSQLException(insert(model))

  def insertAndReturn(model: Model)(implicit ev: FakeId[IdType]): DBIOAction[Entity, NoStream, Write] =
    (table returning table) += WithId.New[IdType](model)

  final def tryInsertAndReturn(model: Model)(implicit ev: FakeId[IdType], ec: ExecutionContext): DBIOAction[Try[Entity], NoStream, Write] =
    mapPSQLException(insertAndReturn(model))

  def insertBatch(models: Iterable[Model])(implicit ev: FakeId[IdType]): DBIOAction[Option[Int], NoStream, Write] =
    table ++= WithId.New[IdType](models)

  def delete(id: IdType)(implicit ec: ExecutionContext): DBIOAction[Boolean, NoStream, Write] = filterIdCompiled(id).delete.map(_ == 1)

}

object Repository {
  sealed abstract class PartialUpdateResults
  case object NotUpdated extends PartialUpdateResults
  case object NotDefined extends PartialUpdateResults

  def mapPSQLException[R, S <: NoStream, E <: Effect](action: DBIOAction[R, S, E])(
      implicit ec: ExecutionContext): DBIOAction[Try[R], NoStream, E] =
    action.asTry.map {
      _.recoverWith {
        case ConstraintViolation(cv) => Failure(cv)
      }
    }

  sealed abstract class ConstraintViolation(msg: String) extends Exception(msg) {
    def constraint: String
  }
  case class IntegrityConstraintViolation(override val constraint: String, msg: String)       extends ConstraintViolation(msg)
  case class UniqueViolation(override val constraint: String, msg: String)                    extends ConstraintViolation(msg)
  case class ForeignKeyViolation(override val constraint: String, table: String, msg: String) extends ConstraintViolation(msg)
  case class CheckViolation(override val constraint: String, table: String, msg: String)      extends ConstraintViolation(msg)

  object ConstraintViolation {
    val INTEGRITY_CONSTRAINT_VIOLATION = "23000"
    val RESTRICT_VIOLATION             = "23001"
    val FOREIGN_KEY_VIOLATION          = "23503"
    val UNIQUE_VIOLATION               = "23505"
    val CHECK_VIOLATION                = "23514"

    private def maybePsqlToConstraintViolation(pSQLException: PSQLException): Option[ConstraintViolation] = {
      val serverError = pSQLException.getServerErrorMessage
      val msg         = pSQLException.getMessage
      pSQLException.getSQLState match {
        case INTEGRITY_CONSTRAINT_VIOLATION => Some(IntegrityConstraintViolation(serverError.getConstraint, msg))
        case FOREIGN_KEY_VIOLATION          => Some(ForeignKeyViolation(serverError.getConstraint, serverError.getTable, msg))
        case UNIQUE_VIOLATION               => Some(UniqueViolation(serverError.getConstraint, msg))
        case CHECK_VIOLATION                => Some(CheckViolation(serverError.getConstraint, serverError.getTable, msg))
        case _                              => None
      }
    }
    def unapply(ex: Throwable): Option[ConstraintViolation] = ex match {
      case (psqlException: PSQLException) => maybePsqlToConstraintViolation(psqlException)
      case _                              => None
    }
  }
}
