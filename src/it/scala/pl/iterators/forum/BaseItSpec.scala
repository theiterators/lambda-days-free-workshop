package pl.iterators.forum

import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.util.Try

class BaseItSpec extends AsyncFlatSpec with BeforeAndAfterAll with Matchers {
  import pl.iterators.forum.utils.db.PostgresDriver.api._

  val config   = ConfigFactory.load()
  val dbConfig = config.getConfig("db")

  val db = Database.forConfig("db", config)

  protected def databaseAvailable: Boolean =
    Try(db.source.createConnection()) map { conn =>
      conn.close(); true
    } getOrElse false

  protected lazy val databaseName = {
    val dbServer = dbConfig.getString("properties.serverName")
    val dbName   = dbConfig.getString("properties.databaseName")
    s"$dbServer/$dbName"
  }

  override protected def beforeAll() = {
    assume(databaseAvailable, s"Database $databaseName is down")
    super.beforeAll()
  }

  override protected def afterAll() = {
    db.close()
    super.afterAll()
  }

}
