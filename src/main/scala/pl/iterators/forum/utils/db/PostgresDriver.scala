package pl.iterators.forum.utils.db

import com.github.tminglei.slickpg.{ExPostgresProfile, PgDate2Support}
import slick.jdbc.JdbcCapabilities.insertOrUpdate

trait PostgresDriver extends ExPostgresProfile with PgDate2Support {
  override protected def computeCapabilities =
    super.computeCapabilities + insertOrUpdate

  override val api = PostgresAPI

  object PostgresAPI extends API with DateTimeImplicits
}

object PostgresDriver extends PostgresDriver
