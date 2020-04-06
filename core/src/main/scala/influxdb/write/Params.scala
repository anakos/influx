package influxdb.write

import cats.syntax.option._
import influxdb.types._
import influxdb.types.Parameter.Precision
import influxdb.types.Parameter.Consistency

final case class Params(
  dbName: String,
  points: String,
  precision: Option[Precision],
  consistency: Option[Consistency],
  retentionPolicy: Option[String]
) { self =>
  def withPrecision(precision: Precision): Params =
    self.copy(precision = precision.some)
  def withConsistency(consistency: Consistency): Params =
    self.copy(consistency = consistency.some)
  def withRetentionPolicy(retentionPolicy: String): Params =
    self.copy(retentionPolicy = retentionPolicy.some)

  def toMap(): Map[String, String] =
    Map("db" -> self.dbName) ++
      precision.map { p => "precision" -> p.toString }.toMap ++
      consistency.map { c => "consistency" -> c.toString }.toMap ++
      retentionPolicy.map { r => "rp" -> r }.toMap
}
object Params {
  def default(dbName: String, point: Point): Params =
    Params(dbName, point.serialize(), none, none, none)
  def bulk(dbName: String, points: List[Point]): Params =
    Params(dbName, points.map(_.serialize()).mkString("\n"), none, none, none)
}