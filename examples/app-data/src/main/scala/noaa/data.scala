package noaa

import cats._
import cats.instances.all._
import cats.syntax.apply._
import cats.syntax.show._
import influxdb.query.QueryResults
import influxdb.query.types._
import java.time.Instant
import scala.collection.immutable.ListMap
import influxdb.query.FieldValidator
import influxdb.Precision
import influxdb.Timestamp

final case class MeasurementName(unwrap: String) {
  override def toString() = this.show
}
object MeasurementName {
  implicit val show: Show[MeasurementName] = Show.show { _.unwrap }
  
  implicit val parser: QueryResults[MeasurementName] =
    new QueryResults[MeasurementName] {
      override def parseWith(
        _precision: Option[Precision],
        name: Option[String],
        tags: ListMap[String, Value],
        data: ListMap[String, Nullable]
      ): Either[String, MeasurementName] =
        FieldValidator.byName("name") { _.asString().map(MeasurementName(_)) }
          .run(data)
    }
}

final case class Count(unwrap: Long)
object Count {
  implicit val show: Show[Count] =
    Show.show { case Count(x) =>
      x.toString()
    }

  implicit val parser: QueryResults[Count] =
    new QueryResults[Count] {
      def parseWith(_precision: Option[Precision], 
                    name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, Count] =
        FieldValidator.byName("count") { _.asNum().map(x => Count(x.toLong)) }
          .run(data)
    }
}

final case class SeaLevel(time: Instant, description: String, location: String, level: BigDecimal) {
  override def toString() = s"$time $description $location $level"
}
object SeaLevel {

  def requireTime(precision: Option[Precision]) =
    Timestamp.validator("time", precision).map(_.unwrap)

  val requireDescription =
    FieldValidator.byName("level description") { _.asString() }

  val requireLocation = 
    FieldValidator.byName("location") { _.asString() }

  val requireLevel = 
    FieldValidator.byName("water_level") { _.asNum() }

  implicit val parser: QueryResults[SeaLevel] =
    new QueryResults[SeaLevel] {
      def parseWith(precision: Option[Precision], 
                    name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, SeaLevel] =
        (requireTime(precision), requireDescription, requireLocation, requireLevel)
          .mapN(SeaLevel(_,_,_,_))
          .run(data)
    }
}