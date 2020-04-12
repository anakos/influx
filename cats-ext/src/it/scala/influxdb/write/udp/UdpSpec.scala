package influxdb
package write.udp

import cats.data._
import cats.effect._
import cats.syntax.apply._

import influxdb.http
import influxdb.query
import influxdb.query.{DB => ReadDB}
import influxdb.write.Point
import org.specs2.mutable

import scala.concurrent.duration._

class UdpSpec extends mutable.Specification with InfluxDbContext[UdpSpec.Env] { sequential
  import UdpSpec._

  override val dbName: String = "_test_database_udp"

  override val env =
    (http.Client.create(defaultConfig()), Client.create(Client.Config("localhost", 8086)))
      .mapN((_,_))

  "write" >> {
    "single point" >> { x: Env =>
      withDb(
        for {
          _       <- DB.write[Env](
            Point.withDefaults("test_measurement").addField("value", 123).addTag("tag_key", "tag_value")
          )
          _       <- ReaderT.liftF(IO.sleep(1.second)) // to allow flushing to happen inside influx
          results <- ReadDB.query[Env, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
          )
        } yield results
      ).run(x).unsafeRunSync() must beLike {
        case Vector(elem) => elem.value must_=== 123
      }
    }

    "bulk points" >> { x: Env =>
      withDb(
        for {
          ts      <- Clock.create[RIO[Env, ?]].realTime(java.util.concurrent.TimeUnit.MILLISECONDS) 
          _       <- DB.bulkWrite[Env](List(
            Point.withDefaults("test_measurement", ts).addField("value", 1).addTag("tag_key", "tag_value"),
            Point.withDefaults("test_measurement", ts + 1).addField("value", 2).addTag("tag_key", "tag_value"),
            Point.withDefaults("test_measurement", ts + 2).addField("value", 3).addTag("tag_key", "tag_value")
          ))
          _       <- Timer[RIO[Env, ?]].sleep(1.second) // to allow flushing to happen inside influx
          results <- ReadDB.query[Env, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
          )
        } yield results
      ).run(x).unsafeRunSync() must beLike {
        case series => series must have size 3
      }
    }
  }
}
object UdpSpec {
  type Env = (HttpClient, Client)
}