package influxdb
package write.udp

import cats.data._
import cats.effect._
import cats.syntax.apply._
import cats.syntax.option._
import influxdb.http
import influxdb.http.{api, Config}
import influxdb.manage.db
import influxdb.query
import influxdb.types._
import influxdb.write.udp

import org.specs2.execute._
import org.specs2.mutable
import org.specs2.matcher.IOMatchers

import scala.concurrent.duration._

class UdpSpec extends mutable.Specification with InfluxUdpContext with IOMatchers {
  sequential

  override val dbName: String = "_test_database_udp"

  "write" >> {
    "single point" >> { x: Env =>
      val result = for {
        _       <- db.create[Env](dbName)
        _       <- udp.write[Env](
          Point.withDefaults("test_measurement").addField("value", 123).addTag("tag_key", "tag_value")
        )
        _       <- ReaderT.liftF(IO.sleep(1.second)) // to allow flushing to happen inside influx
        results <- query.series[Env](
          query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
        )
        _       <- db.drop[Env](dbName)
      } yield results

      result.run(x).unsafeRunSync() must beLike {
        case query.Result(series) =>
          series.head.records must have size 1
          series.head.records.head("value").asNum() must_=== BigDecimal(123).some
      }
    }

    "bulk points" >> { x: Env =>
      val result = for {
        _       <- db.create[Env](dbName)
        ts      <- Clock.create[RIO[Env, ?]].realTime(java.util.concurrent.TimeUnit.MILLISECONDS) 
        _       <- udp.bulkWrite[Env](List(
          Point.withDefaults("test_measurement", ts).addField("value", 1).addTag("tag_key", "tag_value"),
          Point.withDefaults("test_measurement", ts + 1).addField("value", 2).addTag("tag_key", "tag_value"),
          Point.withDefaults("test_measurement", ts + 2).addField("value", 3).addTag("tag_key", "tag_value")
        ))
        _       <- Timer[RIO[Env, ?]].sleep(1.second) // to allow flushing to happen inside influx
        results <- query.series[Env](
          query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
        )
        _       <- db.drop[Env](dbName)
      } yield results

      result.run(x).unsafeRunSync() must beLike {
        case query.Result(series) =>
          series.head.records must have size 3
      }
    }
  }
}

trait InfluxUdpContext extends org.specs2.specification.ForEach[(http.Handle, udp.Client)] {
  type Env = (http.Handle, udp.Client)

  def dbName: String

  override def foreach[R: AsResult](f: Env => R): Result = {
    val cfg = Config(
      Config.Client.default(),
      Config.Connect.http("localhost", 8086, "influx_user".some, "influx_password".some)
    ) 

    (http.Handle.create(cfg), udp.Client.create(udp.Client.Config("localhost", 8086)))
      .mapN((_,_))
      .use { x => IO { implicitly[AsResult[R]].asResult(f(x)) } }
      .unsafeRunSync()
  }
}