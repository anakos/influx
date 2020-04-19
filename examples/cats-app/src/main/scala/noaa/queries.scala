package noaa

import cats.effect._
import influxdb.query
import influxdb.query.{DB, QueryResults}

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