package noaa

import cats.effect._
import cats.effect.syntax.bracket._
import cats.syntax.flatMap._
import cats.syntax.functor._
import influxdb.manage.db

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Env.create("localhost", 8086, "influx_user", "influx_password")
      // .use { withNoaaWaterDatabase(app).run(_) }
      .use { (db.create[Env](DEFAULT_DB_NAME) >> app).run(_) }
      .as(ExitCode.Success)

  def withNoaaWaterDatabase[A](action: App[A]): App[Unit] =
    db.create[Env](DEFAULT_DB_NAME)
      .bracket(_ => action) { _ =>
        db.drop[Env](DEFAULT_DB_NAME) >> Logger.info(s"$DEFAULT_DB_NAME dropped")
      }
      .void
  
  val app: App[Unit] =
    for {
      _            <- Logger.info[Env](s"$DEFAULT_DB_NAME created, commence data import")
      
      count        <- SampleData.readIntoInflux(DEFAULT_DB_NAME)

      _            <- Logger.info[Env](s"read $count total data points into $DEFAULT_DB_NAME")
      
      measurements <- Queries.getMeasurements()

      _            <- Logger.info[Env](
        s"""$DEFAULT_DB_NAME contains the following measurements:
           |
           |${measurements.mkString("\n")}
           |
         """.stripMargin
      )

      waterLevel   <- Queries.countNonNullValues("water_level", "h2o_feet")

      _            <- Logger.info[Env](
        s"$DEFAULT_DB_NAME.h2o_feet.water_level.count == $waterLevel"
      )

      waterLevel   <- Queries.selectFirstFiveObs[SeaLevel]("h2o_feet")

      _            <- Logger.info[Env](
        s"""five observations in the measurement h2o_feet:
           |
           |${waterLevel.mkString("\n")}
           |
          """.stripMargin
      )

    } yield ()
}

/*
curl -G 'http://localhost:8086/query' --data-urlencode "db=NOAA_water_database" \
  --data-urlencode "chunked=true" \
  --data-urlencode "chunk_size=20" \
  --data-urlencode "u=influx_user" \
  --data-urlencode "p=influx_password" \
  --data-urlencode "q=SELECT * FROM h2o_feet"
 */