package influxdb
package manage

import cats.instances.option._
import cats.syntax.apply._
import cats.syntax.functor._
import influxdb.http.api
import influxdb.query
import io.circe.generic.auto._

object users {
  def create[E: http.Has](username: String, password: String) =
    exec[E, api.Statement](s"CREATE USER $username WITH PASSWORD '$password'")
      .void

  def createClusterAdmin[E: http.Has](username: String, password: String) =
    exec[E, api.Statement](s"CREATE USER $username WITH PASSWORD '$password' WITH ALL PRIVILEGES")
      .void
  
  def dropUser[E: http.Has](username: String) =
    exec[E, api.Statement](s"DROP USER $username")
      .void

  def showUsers[E: http.Has]() =
    query.single[E, api.SingleSeries](query.Params.singleQuery("SHOW USERS"))

  def setUserPassword[E: http.Has](username: String, password: String) =
    exec[E, api.Statement]("SET PASSWORD FOR %s='%s'".format(username, password))
      .void

  def grantPrivileges[E: http.Has](username: String, database: String, privilege: Privilege) =
    exec[E, api.Statement]("GRANT %s ON %s TO %s".format(privilege, database, username))

  def revokePrivileges[E: http.Has](username: String, database: String, privilege: Privilege) =
    exec[E, api.Statement]("REVOKE %s ON %s FROM %s".format(privilege, database, username))

  def makeClusterAdmin[E: http.Has](username: String) =
    exec[E, api.Statement]("GRANT ALL PRIVILEGES TO %s".format(username))

  def userIsClusterAdmin[E: http.Has](username: String) = {
    showUsers[E]().map { result =>
      result.series
        .headOption
        .flatMap {
          _.records
            .find { record =>
              (record("user").ifString(_ == username), record("admin").ifBool(_ == true))
                .mapN { _ && _ }
                .getOrElse(false)
            } 
        }
        .isDefined
    }
  }

  def escapePassword(password: String) =
    password.replaceAll("(['\n])", "\\\\$1")  
}
sealed abstract class Privilege
case object READ extends Privilege
case object WRITE extends Privilege
case object ALL extends Privilege