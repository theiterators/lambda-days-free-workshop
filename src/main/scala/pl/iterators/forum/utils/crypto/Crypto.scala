package pl.iterators.forum.utils.crypto

import java.security.SecureRandom

import org.jasypt.util.password._
import pl.iterators.forum.utils.db.PostgresDriver.api._

import scala.util.Random

object Crypto {

  trait Password {
    def encrypted: String
    val verify: String => Boolean
  }

  trait TypeMappers {
    implicit val passwordMapping: BaseColumnType[Password] = MappedColumnType.base(_.encrypted, EncryptedPassword)
  }

  private[this] case class EncryptedPassword(encrypted: String) extends Password {
    override val verify: (String) => Boolean = passwordCheckFunction(this)
  }

  private val encryptor = new StrongPasswordEncryptor

  val encryptFunction: String => Password = plain => EncryptedPassword(encryptor encryptPassword plain)
  val passwordCheckFunction: Password => String => Boolean = encryptedPassword =>
    plain => encryptor.checkPassword(plain, encryptedPassword.encrypted)

  private val secureRandom = new Random(new SecureRandom())

  def generateStrongRandomAlphanumeric(minLength: Int, maxLength: Int): String = {
    val length = if (minLength == maxLength) minLength else minLength + Random.nextInt(maxLength - minLength + 1)
    secureRandom.alphanumeric.take(length).mkString
  }
}
