package influxdb
package manage

import cats.instances.option._
import cats.syntax.apply._
import influxdb.query.{DB, QueryResults}
import influxdb.query.types._
import scala.collection.immutable.ListMap
import influxdb.manage.User.AdminUser

object users {
  def create[E: influxdb.Has](username: String, password: String) =
    exec[E](s"CREATE USER $username WITH PASSWORD '$password'")

  def createClusterAdmin[E: influxdb.Has](username: String, password: String) =
    exec[E](s"CREATE USER $username WITH PASSWORD '$password' WITH ALL PRIVILEGES")
  
  def dropUser[E: influxdb.Has](username: String) =
    exec[E](s"DROP USER $username")

  def showUsers[E: influxdb.Has]() =
    DB.query[E, User](query.Params.singleQuery("SHOW USERS"))

  def setUserPassword[E: influxdb.Has](username: String, password: String) =
    exec[E]("SET PASSWORD FOR %s='%s'".format(username, password))

  def grantPrivileges[E: influxdb.Has](username: String, database: String, privilege: Privilege) =
    exec[E]("GRANT %s ON %s TO %s".format(privilege, database, username))

  def revokePrivileges[E: influxdb.Has](username: String, database: String, privilege: Privilege) =
    exec[E]("REVOKE %s ON %s FROM %s".format(privilege, database, username))

  def makeClusterAdmin[E: influxdb.Has](username: String) =
    exec[E]("GRANT ALL PRIVILEGES TO %s".format(username))

  def userIsClusterAdmin[E: influxdb.Has](username: String) =
    showUsers[E]().map {
      _.exists {
        case AdminUser(name) => name == username 
        case _ => false
      }
    
    }

  def escapePassword(password: String) =
    password.replaceAll("(['\n])", "\\\\$1")  
}
sealed abstract class Privilege
case object READ extends Privilege
case object WRITE extends Privilege
case object ALL extends Privilege

sealed abstract class User(val name: String) extends Product with Serializable
object User {
  final case class AdminUser(override val name: String) extends User(name)
  final case class NonAdminUser(override val name: String)  extends User(name)

  implicit val parser: QueryResults[User] =
    new QueryResults[User] {
      def parseWith(name  : Option[String],
                    tags  : ListMap[String, Value],
                    fields: ListMap[String, Nullable]): Either[String, User] =
        (fields.get("user").flatMap(_.asString()), fields.get("admin").flatMap(_.asBool()))
          .mapN { (userName, isAdmin) =>
            if (isAdmin) AdminUser(userName)
            else NonAdminUser(userName)
          }
          .toRight("could not get user and admin fields from result")
    }
}