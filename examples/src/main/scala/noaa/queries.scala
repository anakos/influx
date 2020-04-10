package noaa

import cats._
import cats.effect._
import cats.instances.all._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.show._
import influxdb.query
import influxdb.query.{DB, QueryResults}
import influxdb.query.types._
import java.time.Instant
import scala.collection.immutable.ListMap
import influxdb.query.FieldValidator

object Queries {
  def mkParams(q: String): query.Params =
    query.Params.singleQuery(q, DEFAULT_DB_NAME)

  def getMeasurements(): App[Vector[MeasurementName]] =
    DB.query[Env, MeasurementName](mkParams("SHOW measurements"))

  def countNonNullValues(fieldName: String, measurementName: String): App[Long] =
    DB.query[Env, Count](mkParams(s"""SELECT COUNT("$fieldName") FROM $measurementName"""))
      .flatMapF {
        case Vector(Count(x)) =>
          IO.pure(x)
        case results          =>
          IO.raiseError(new RuntimeException(s"expected singleton list, got: ${results.mkString(", ")}"))
      }

  def selectFirstFiveObs[A : QueryResults](measurementName: String): App[Vector[A]] =
    DB.query[Env, A](mkParams(s"""SELECT * FROM $measurementName LIMIT 5"""))

  def showTags(measurementName: String): App[List[String]] = ???
}

final case class MeasurementName(unwrap: String) {
  override def toString() = this.show
}
object MeasurementName {
  implicit val show: Show[MeasurementName] = Show.show { _.unwrap }
  
  implicit val parser: QueryResults[MeasurementName] =
    new QueryResults[MeasurementName] {
      override def parseWith(
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
      def parseWith(name: Option[String],
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

  val requireTime =
    FieldValidator.byName("time") { _.asString() }
      .flatMapF { x =>
        Either.catchNonFatal(java.time.Instant.parse(x))
          .leftMap(ex => s"could not decode timestamp: ${ex.getMessage}")
      }

  val requireDescription =
    FieldValidator.byName("level description") { _.asString() }

  val requireLocation = 
    FieldValidator.byName("location") { _.asString() }

  val requireLevel = 
    FieldValidator.byName("water_level") { _.asNum() }

  implicit val parser: QueryResults[SeaLevel] =
    new QueryResults[SeaLevel] {
      def parseWith(name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, SeaLevel] =
        (requireTime, requireDescription, requireLocation, requireLevel)
          .mapN(SeaLevel(_,_,_,_))
          .run(data)
    }
}