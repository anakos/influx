package noaa

import influxdb._
import influxdb.query.QueryResults
import zio._

object Queries {
  def mkParams(q: String): query.Params =
    query.Params.singleQuery(q, DEFAULT_DB_NAME)

  def getMeasurements() =
    InfluxDB.query[MeasurementName](mkParams("SHOW measurements"))

  def countNonNullValues(fieldName: String, measurementName: String) =
    InfluxDB.query[Count](mkParams(s"""SELECT COUNT("$fieldName") FROM $measurementName"""))
      .flatMap {
        case Vector(Count(x)) =>
          ZIO.succeed(x)
        case results          =>
          ZIO.fail(
            InfluxException.UnexpectedResponse("expected singleton list only", s"[ ${results.mkString(", ")} ]")
          )
      }

  def selectFirstFiveObs[A : QueryResults](measurementName: String) =
    InfluxDB.query[A](mkParams(s"""SELECT * FROM $measurementName LIMIT 5"""))

  def selectAll[A : QueryResults](measurementName: String, size: Long) =
    InfluxDB.queryChunked[A](
      mkParams(s"""SELECT * FROM $measurementName"""),
      influxdb.query.ChunkSize.withSize(influxdb.Natural.create(size).get)
    )

  // def showTags(measurementName: String): App[List[String]] = ???
}