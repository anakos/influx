package influxdb
package http

import cats.effect._

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._

import influxdb.InfluxException.HttpException

import org.specs2.mutable
import org.specs2.matcher.IOMatchers
import org.specs2.specification.AfterAll

class HandleSpec extends mutable.Specification with AfterAll with IOMatchers {
  sequential

  val (server, shutdown) = HandleSpec.initWireMock(
    wireMockConfig().port(64011)
      .containerThreads(10)
      .jettyAcceptors(1)
      .httpsPort(64012)
  )

  override def afterAll() =
    shutdown.unsafeRunSync()

  "Handle.get" >> {
    "https requests are received" >> {
      server.stubFor(get(urlEqualTo(HandleSpec.path)).willReturn(aResponse().withStatus(200).withBody("")))

      HandleSpec.get(
        http.Config.defaultHttps("localhost", 64012).copy(
          client = Config.Client.default().setAcceptAnyCertificate(true)
        )
      )
      .unsafeRunSync() must beRight.like {
        case HttpResponse(code, content) =>
          code must_=== 200
          content must_=== ""
      }
    }

    "http requests are received" >> {
      server.stubFor(get(urlEqualTo(HandleSpec.path)).willReturn(aResponse().withStatus(200).withBody("")))
      HandleSpec
        .get(http.Config.defaultHttp("localhost", 64011))
        .unsafeRunSync() must beRight.like {
          case HttpResponse(code, content) =>
            code must_=== 200
            content must_=== ""
        }
    }


    "error responses are handled correctly" >> {
      server.stubFor(get(urlEqualTo(HandleSpec.path))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withBody("")))

      HandleSpec.get(http.Config.defaultHttp("localhost", 64011))
        .unsafeRunSync() must beLeft[Throwable].like {
          case HttpException(_,code) => code must beSome[Int].like {
            case code => code must_=== 500
          }
        }
    }
  }

  "Client Failures without status code" >> {
    "on connection refused" >> {
      HandleSpec.get(Config.defaultHttp("localhost", 64010))
        .unsafeRunSync() must beLeft[Throwable].like {
          case HttpException(_,code) => code must beNone
        }
    }

    "if request takes too long" >> {
      server.stubFor(
        get(urlEqualTo(HandleSpec.path))
          .willReturn(
            aResponse()
              .withFixedDelay(200)
              .withStatus(200)
              .withBody("a"))
      )

      HandleSpec.get(
        Config.defaultHttp("localhost", 64011).copy(
          client = Config.Client.default().setRequestTimeout(50)
        )
      )
      .unsafeRunSync() must beLeft[Throwable].like {
        case HttpException(_,code) => code must beNone
      }
    }

    "if the connection takes too long to establish" >> {
      HandleSpec.get(
        Config.defaultHttp("192.0.2.1", 64011).copy(
          client = Config.Client.default().setConnectTimeout(50)
        )
      )
      .unsafeRunSync() must beLeft[Throwable].like {
        case HttpException(_,code) => code must beNone
      }
    }
  }
}
object HandleSpec {
  val path = "/query"

  lazy val defaultConfiguration: WireMockConfiguration =
    wireMockConfig().port(64011)
      .containerThreads(10)
      .jettyAcceptors(1)
      .httpsPort(64012)

  def initWireMock(config: WireMockConfiguration): (WireMockServer, IO[Unit]) = 
    Resource.make(IO { new WireMockServer(config) }) { server => IO { server.shutdown() } }
      .evalTap { server =>
        IO { server.start() }
      }
      .allocated[IO, WireMockServer]
      .unsafeRunSync()

  def get(config: http.Config): IO[Either[Throwable, HttpResponse]] =
    Handle.create(config)
      .use { _.get(path, Map.empty) }
      .attempt
}