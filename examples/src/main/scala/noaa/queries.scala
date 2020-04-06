package noaa

import cats._
import cats.effect._
import cats.instances.all._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.traverse._
import influxdb.query
import io.circe._
import java.time.Instant

object Queries {
  def mkParams(q: String): query.Params =
    query.Params.singleQuery(q, DEFAULT_DB_NAME)

  def getMeasurements(): App[Measurements] =
    query.single[Env, Measurements](mkParams("SHOW measurements"))
      .flatMapF {
        case query.Result(List(measurements)) =>
          measurements.pure[IO]
        case results =>
          IO.raiseError(
            new RuntimeException(s"expected a single result with all the measurements, received $results")
          )
      }

  def countNonNullValues(fieldName: String, measurementName: String): App[Long] =
    query.single[Env, Count](
      mkParams(s"""SELECT COUNT("$fieldName") FROM $measurementName""")
    )
    .flatMapF {
      case query.Result(List(Count(value))) =>
        value.pure[IO]
      case query.Result(results) =>
        IO.raiseError(
          new RuntimeException(s"expected a single result providing a count, received $results")
        )
    }  

  def selectFirstFiveObs[A : Decoder](measurementName: String): App[A] =
    query.single[Env, A](mkParams(s"""SELECT * FROM $measurementName LIMIT 5"""))
    .flatMapF {
      case query.Result(List(vals)) =>
        vals.pure[IO]
      case query.Result(results) =>
        IO.raiseError(
          new RuntimeException(s"expected a single result providing a count, received $results")
        )
    }

  def showTags(measurementName: String): App[List[String]] = ???
}

final case class Measurements(names: Vector[String])
object Measurements {
  implicit val show: Show[Measurements] = Show.show { case Measurements(vals) =>
    vals.mkString(" | ")
  }

  implicit val decoder: Decoder[Measurements] =
    Decoder.instance[Measurements] { csr =>
      csr.downField("values").as[Vector[Vector[String]]]
        .map(values => Measurements(values.flatten))
    }
}

final case class Count(unwrap: Long)
object Count {
  implicit val show: Show[Count] =
    Show.show { case Count(x) =>
      x.toString()
    }

  implicit val decoder: Decoder[Count] =
    Decoder[influxdb.http.api.SingleSeries]
      .emap { series =>
        for {
          record <- series.records.headOption.toRight("no series data was returned")
          _      <- "expected a single record".asLeft.whenA(series.records.size != 1)
          result <- record("count").asNum().toRight("no numeric count value in record")
        } yield Count(result.toLong)        
      }
}

final case class SeaLevel(time: Instant, description: String, location: String, level: BigDecimal) {
  override def toString() = s"$time $description $location $level"
}
final case class SeaLevelSeries(unwrap: Vector[SeaLevel])
object SeaLevelSeries {
  implicit val show: Show[SeaLevelSeries] = Show.show { _.unwrap.mkString("\n") }

  implicit val decoder: Decoder[SeaLevelSeries] =
    Decoder[influxdb.http.api.SingleSeries]
      .emap { series =>
        series.records
          .traverse { rec =>
            (rec("time").asString().toRight(s"no time reading present")
              .flatMap { x => Either.catchNonFatal(java.time.Instant.parse(x)).leftMap(ex => s"could not decode timestamp: ${ex.getMessage}") },
              rec("level description").asString().toRight("no description present"),
              rec("location").asString().toRight("no location present"),
              rec("water_level").asNum().toRight("no level present")).mapN { SeaLevel(_,_,_,_) }
          }
          .map { SeaLevelSeries(_) }
      }
}
