package noaa

import cats.data.ReaderT
import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.option._
import influxdb.{write => Write}
import influxdb.write.DB
import java.io.InputStream
import scala.concurrent.duration._

object SampleData {
  val dataSource: App[InputStream] =
    ReaderT.liftF(
      IO {
        java.net.URI
          .create("https://s3.amazonaws.com/noaa.water-database/NOAA_data.txt")
          .toURL()
          .openStream()
      }
    )
      
  def readPointData(blocker: Blocker)(implicit cs: ContextShift[IO]): fs2.Stream[App, String] =
    fs2.io.readInputStream(
      dataSource,
      100,
      blocker
    )
    .through(fs2.text.utf8Decode)
    .through(fs2.text.lines)
    .filter(x => x.startsWith("h2o_") || x.startsWith("average_"))

  def writeBatch(counter: Ref[App, Long], dbName: String, points: fs2.Chunk[String]): App[Unit] =
    DB.write[Env](Write.Params(dbName, points.toList.mkString("\n"), none, none, none)) >>
      counter.update(_ + points.size)

  def readIntoInflux(dbName: String): App[Long] =
    Env.blocker.view.flatMap { blocker =>
      Env.timer.view.flatMap { implicit timer =>
        Env.contextShift.view.flatMap { implicit cs =>
          Ref.of[App, Long](0).flatMap { counter =>
            readPointData(blocker)
              .groupWithin(100, 1.second)
              .observe(
                _.evalMap(_ =>
                  counter.get
                    .flatMap { n => Logger.warn[Env](s"processed $n points").whenA(n % 1000 == 0) }
                )
              )
              .through(_.evalMap(writeBatch(counter, dbName, _)))
              .compile
              .drain >> counter.get        
          }
        }
      }
    }
}