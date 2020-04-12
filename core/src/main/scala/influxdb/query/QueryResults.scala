package influxdb.query

import cats.instances.either._
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.flatMap._
import scala.collection.immutable.ListMap
import influxdb.query.types._

/**
  * Conversions for types that are constructed from InfluxDB JSON series data.
  * TODO: are tags still a thing?
  */
trait QueryResults[A] {
  def parseWith(name  : Option[String],
                tags  : ListMap[String, Value],
                fields: ListMap[String, Nullable]): Either[String, A]

  def parseWithRaw(name   : Option[String],
                   tags   : ListMap[String, Value],
                   columns: Vector[String],
                   values : Vector[Nullable]): Either[String, A] =
    validateFields(columns, values)
      .flatMap { parseWith(name, tags, _) }

  def validateFields(columns: Vector[String], values: Vector[Nullable]): Either[String, ListMap[String, Nullable]] =
    s"mismatched number of columns [${columns.size}] and values [${values.size}]"
      .asLeft[Unit]
      .whenA(columns.size != values.size) >> ListMap.from(columns.zip(values)).asRight
}
object QueryResults {
  def apply[A](implicit qr: QueryResults[A]): QueryResults[A] =
    qr
  
  implicit val unit: QueryResults[Unit] =
    new QueryResults[Unit] {
      def parseWith(name   : Option[String],
                    tags   : scala.collection.immutable.ListMap[String, Value],
                    fields: ListMap[String, Nullable]): Either[String, Unit] =
        ().asRight[String]
    }
}