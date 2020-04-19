package influxdb
package manage

import cats.syntax.either._
import influxdb.query.{Params, QueryResults}
import influxdb.query.types._
import scala.collection.immutable.ListMap
import zio._

object db {
  def show(): ZIO[InfluxDB, InfluxException, Vector[DbName]] =
    InfluxDB.query[DbName](Params.singleQuery("SHOW DATABASES"))

  def create(name: String): ZIO[InfluxDB, InfluxException, Unit] =
    InfluxDB.exec(Params.singleQuery(s"""CREATE DATABASE "$name""""))

  def drop(name: String): ZIO[InfluxDB, InfluxException, Unit] =
    InfluxDB.exec(Params.singleQuery(s"""DROP DATABASE "$name""""))

  def exists(name: String): ZIO[InfluxDB, InfluxException, Boolean] =
    show().map(_.find(_.unwrap == name).isDefined)
}
final case class DbName(unwrap: String)
object DbName {
  implicit val parser: QueryResults[DbName] =
    new QueryResults[DbName] {
      def parseWith(_precision: Option[Precision],
                    name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, DbName] =
        DbName(data.get("name").flatMap { _.asString() }.getOrElse(""))
          .asRight
    }
}