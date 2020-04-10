package influxdb
package manage

import cats.syntax.either._
import influxdb.query.{DB, Params, QueryResults}
import influxdb.query.types._

import scala.collection.immutable.ListMap

object db {
  def show[E : influxdb.Has](): RIO[E, Vector[DbName]] =
    DB.query[E, DbName](Params.singleQuery("SHOW DATABASES"))

  def create[E : influxdb.Has](name: String): RIO[E, Unit] =
    exec[E](s"""CREATE DATABASE "$name"""")

  def drop[E : influxdb.Has](name: String) =
    exec[E](s"""DROP DATABASE "$name"""")

  def exists[E : influxdb.Has](name: String) =
    show[E]().map(_.find(_.unwrap == name).isDefined)
}
final case class DbName(unwrap: String)
object DbName {
  implicit val parser: QueryResults[DbName] =
    new QueryResults[DbName] {
      def parseWith(name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, DbName] =
        DbName(data.get("name").flatMap { _.asString() }.getOrElse(""))
          .asRight
    }
}