package pl.iterators.forum.utils.db

import PostgresDriver.api._
import slick.lifted.{MappedProjection, ProvenShape}

abstract class TableWithId[Id: BaseColumnType, A](tag: Tag, tableName: String) extends Table[WithId[Id, A]](tag, tableName) {
  protected val idColumnName = "id"
  final def id: Rep[Id]      = column[Id](idColumnName, O.PrimaryKey, O.AutoInc)

  def model: MappedProjection[A, _]
  override final def * : ProvenShape[WithId[Id, A]] = (id, model) <> (WithId.tupled[Id, A], WithId.unapply[Id, A])

}
