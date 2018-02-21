import org.flywaydb.sbt.FlywayPlugin
import org.flywaydb.sbt.FlywayPlugin.autoImport._
import sbt.Keys._
import sbt._

object PostgresMigrations {
  private val env = sys.env

  def dbName     = env.getOrElse("POSTGRESQL_DB", "iterators-forum")
  def testDbName = env.getOrElse("POSTGRESQL_TEST_DB", s"$dbName-test")
  def dbServer   = env.getOrElse("POSTGRESQL_SERVICE_HOST", "localhost")
  def dbUser     = env.getOrElse("POSTGRESQL_USERNAME", sys.props("user.name"))
  def dbPassword = env.getOrElse("POSTGRESQL_PASSWORD", "")
  def dbUrl      = s"jdbc:postgresql://$dbServer/$dbName"
  def dbUrlForIt = s"jdbc:postgresql://$dbServer/$testDbName"

  private def commonSettings = Seq(
    flywayUser := dbUser,
    flywayPassword := dbPassword,
    flywayLocations := Seq(s"filesystem:${(resourceDirectory in Compile).value.getPath}/db/migrations")
  )

  def settings: Seq[Setting[_]] = commonSettings ++ Seq(
    flywayUrl := dbUrl
  )

  def itSettings: Seq[Setting[_]] = FlywayPlugin.flywayBaseSettings(IntegrationTest) ++ commonSettings ++ Seq(
    flywayUrl := dbUrlForIt
  )

}

