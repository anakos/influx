package influxdb.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._

import influxdb.InfluxException

import org.specs2.mutable
import org.specs2.specification.AfterAll
import zio._

/**
  * NOTE: this is effectively a duplicate of what's in the cats package. 
  * TODO: find a sensible way to remove the duplication.
  *
  */
class ClientSpec extends mutable.Specification with AfterAll {
  sequential

  val (server, shutdown) = ClientSpec.initWireMock(
    wireMockConfig().port(64111)
      .containerThreads(10)
      .jettyAcceptors(1)
      .httpsPort(64112)
  )

  override def afterAll() =
    Runtime.default.unsafeRun(shutdown)

  "Client.get" >> {
    "https requests are received" >> {
      server.stubFor(get(urlEqualTo(ClientSpec.path)).willReturn(aResponse().withStatus(200).withBody("")))

      Runtime.default.unsafeRun(
        ClientSpec.get(
          Config.defaultHttps("localhost", 64012).copy(
            client = Config.Client.default().setAcceptAnyCertificate(true)
          )
        )
      ) must_=== HttpResponse(200, "")
    }

    "http requests are received" >> {
      server.stubFor(get(urlEqualTo(ClientSpec.path)).willReturn(aResponse().withStatus(200).withBody("")))

      Runtime.default.unsafeRun(
        ClientSpec.get(Config.defaultHttp("localhost", 64111))
      ) must_=== HttpResponse(200, "")
    }

    "error responses are handled correctly" >> {
      server.stubFor(get(urlEqualTo(ClientSpec.path))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withBody("")))

      Runtime.default.unsafeRun(
        ClientSpec
          .get(Config.defaultHttp("localhost", 64111))
          .either
      ) must beLeft[InfluxException].like {
          case InfluxException.ServerError(msg) =>
            msg must contain("[status code = 500]")
        }
    }
  }

  "Client Failures without status code" >> {
    "on connection refused" >> {
      Runtime.default.unsafeRun(
        ClientSpec.get(Config.defaultHttp("localhost", 64010))
          .either
      ) must beLeft[Throwable].like {
        case InfluxException.HttpException(_,code) => code must beNone
      }
    }

    "if request takes too long" >> {
      /*
      server.stubFor(
        get(urlEqualTo(ClientSpec.path))
          .willReturn(
            aResponse()
              .withFixedDelay(1000)
              .withStatus(200)
              .withBody("a"))
      )

      ClientSpec.get(
        Config.defaultHttp("localhost", 64111).copy(
          client = Config.Client.default().setRequestTimeout(50)
        )
      )
      .unsafeRunSync() must beLeft[Throwable].like {
        case HttpException(_,code) => code must beNone
      }*/
      pending("TODO: it would seem as though sttp does something that prevents the underlying client from respecting the value of the provided read timeout")
    }

    "if the connection takes too long to establish" >> {
      Runtime.default.unsafeRun(
        ClientSpec.get(
          Config.defaultHttp("192.0.2.1", 64111).copy(
            client = Config.Client.default().setConnectTimeout(50)
          )
        )
        .either
      ) must beLeft[Throwable].like {
        case InfluxException.HttpException(_,code) => code must beNone
      }
    }
  }
}
object ClientSpec {
  import zio._
  val path = "/query"

  lazy val defaultConfiguration: WireMockConfiguration =
    wireMockConfig().port(64111)
      .containerThreads(10)
      .jettyAcceptors(1)
      .httpsPort(64112)

  def initWireMock(config: WireMockConfiguration): (WireMockServer, UIO[Unit]) =
    Runtime.default.unsafeRun(
      for {
        reservation <- ZManaged.makeEffect(new WireMockServer(config)) { server => server.shutdown() }
          .tapM(server => Task { server.start() })
          .reserve
        server      <- reservation.acquire
      } yield (server, reservation.release(Exit.unit).unit)
    )

  def get(config: Config): IO[InfluxException, HttpResponse.Text] =
    Client.layer(config.copy(client = config.client.setAcceptAnyCertificate(true)))
      .orDie
      .build
      .use { _.get.get(path, Map.empty) }    
}