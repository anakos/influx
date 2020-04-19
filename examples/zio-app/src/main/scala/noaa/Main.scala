package noaa

import influxdb.manage.db
import zio._

object Main extends zio.App {
  override def run(args: List[String]) =
    withNoaaWaterDatabase(program)
      .provideLayer(Env.create("localhost", 8086, "influx_user", "influx_password"))
      .fold(ex => {ex.printStackTrace; 1}, _ => 0)

  def withNoaaWaterDatabase[A](action: RIO[Env, A]) =
    ZManaged.make(db.create(DEFAULT_DB_NAME) &> Logging.info(s"$DEFAULT_DB_NAME created")) { _ =>
      (db.drop(DEFAULT_DB_NAME) &> Logging.info(s"$DEFAULT_DB_NAME dropped")).orDie
    }
    .use(_ => action)

  val program: ZIO[Env, Throwable, Unit] =
    for {
       _           <- Logging.info(s"$DEFAULT_DB_NAME created, commence data import")
      count        <- SampleData.readIntoInflux(DEFAULT_DB_NAME)
      _            <- Logging.info(s"read $count total data points into $DEFAULT_DB_NAME")
      measurements <- Queries.getMeasurements()
      _            <- Logging.info(
        s"""$DEFAULT_DB_NAME contains the following measurements:
           |
           |${measurements.mkString("\n")}
           |
         """.stripMargin
      )
      waterLevel   <- Queries.countNonNullValues("water_level", "h2o_feet")

      _            <- Logging.info(s"$DEFAULT_DB_NAME.h2o_feet.water_level.count == $waterLevel")
      waterLevel   <- Queries.selectFirstFiveObs[SeaLevel]("h2o_feet")
      _            <- Logging.info(
        s"""five observations in the measurement h2o_feet:
           |
           |${waterLevel.mkString("\n")}
           |
          """.stripMargin
      )      
      levels_count <- Queries.selectAll[SeaLevel]("h2o_feet", 5).runCount
      _            <- Logging.info(s"""chunked $levels_count items from h2o_feet""")
    } yield ()
}