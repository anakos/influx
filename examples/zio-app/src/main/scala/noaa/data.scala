package noaa

import cats.syntax.option._
import influxdb.InfluxDB
import influxdb.{write => Write}
import zio._
import zio.duration._
import zio.stream._

object SampleData {
  val dataSource =
    ZStream.fromInputStream(
      java.net.URI
        .create("https://s3.amazonaws.com/noaa.water-database/NOAA_data.txt")
        .toURL()
        .openStream()
    )
      
  def readPointData(): Stream[java.io.IOException, String] =
    dataSource.chunks
      .aggregate(ZSink.utf8DecodeChunk)
      .aggregate(ZSink.splitLines)
      .map { _.filter(x => x.startsWith("h2o_") || x.startsWith("average_")) }
      .mapConcat(_.toList)

  def writeBatch(counter: Ref[Long], dbName: String, points: List[String]) =
    InfluxDB.write(Write.Params(dbName, points.mkString("\n"), none, none, none)) >>>
      counter.update(_ + points.size)

  def readIntoInflux(dbName: String) =
    Ref.make(0L).flatMap { counter =>
      readPointData()
        .groupedWithin(100, 1.second)
        .tap(_ =>
          counter.get.flatMap { n =>
            Logging.warn(s"processed $n points")
              .when(n % 1000 == 0)
          }
        )
        .mapM(writeBatch(counter, dbName, _))
        .drain
        .runDrain >>> counter.get
    }
}