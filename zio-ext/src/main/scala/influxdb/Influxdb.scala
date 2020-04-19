package influxdb

import cats.instances.vector._
import cats.syntax.traverse._
import influxdb.{query => Query, write => Write}
import influxdb.http.HttpResponse
import influxdb.query.{ChunkSize, json => JSON, QueryResults}

import zio._
import zio.stream.{Stream, ZStream}
import zio.interop.catz.core._

object InfluxDB {
  trait Service {
    def query[A : QueryResults](params: Query.Params): IO[InfluxException, Vector[A]]
    def queryChunked[A : QueryResults](params: Query.Params, chunkSize: ChunkSize): Stream[InfluxException, A]

    def query_(params: Query.Params): IO[InfluxException, Unit]
    def write(params: Write.Params): IO[InfluxException, Unit]
    def exec(params: Query.Params): IO[InfluxException, Unit]
  }

  def query[A : QueryResults](params: Query.Params): ZIO[InfluxDB, InfluxException, Vector[A]] =
    ZIO.accessM(_.get.query(params))
  def query_(params: Query.Params): ZIO[InfluxDB, InfluxException, Unit] =
    ZIO.accessM(_.get.query_(params))
  def exec(params: Query.Params): ZIO[InfluxDB, InfluxException, Unit] =
    ZIO.accessM(_.get.exec(params))
  def write(params: Write.Params): ZIO[InfluxDB, InfluxException, Unit] =
    ZIO.accessM(_.get.write(params))
  def queryChunked[A : QueryResults](params: Query.Params, chunkSize: ChunkSize): ZStream[InfluxDB, InfluxException, A] =
    ZStream.accessStream(_.get.queryChunked(params, chunkSize))
}

final class LiveService private(val client: http.Client) extends InfluxDB.Service {
  override def query[A : QueryResults](params: Query.Params): IO[InfluxException, Vector[A]] =
    execute(params).flatMap { response =>
      IO.fromEither(influxdb.http.HttpClient.handleResponse(params, response))
    }

  override def query_(params: Query.Params): IO[InfluxException, Unit] =
    execute(params)
      .flatMap { expectEmptyResponse }

  override def queryChunked[A : QueryResults](params: Query.Params, chunkSize: ChunkSize): Stream[InfluxException, A] =
    ZStream.fromEffect(client.getChunked("/query", params.toMap(), chunkSize))
      .flatMap {
        _.content
          .bimap(
            InfluxException.httpException("failure streaming chunked data from InfluxDB", _),
            Chunk.fromByteBuffer(_)
          )
          .via(
            influxdb.json.byteStreamParserC
              .andThen(_.mapError(InfluxException.unexpectedResponse("unable to parse content", _)))
          )
          .mapConcatM(_.toVector.flatTraverse { js => IO.fromEither(JSON.parseQueryResult[A](js, params.precision)) })
      }

  override def write(params: Write.Params): IO[InfluxException, Unit] =
    client.post("/write", params.toMap(), params.points)
      .flatMap {
        case response if response.code == 204 => IO.unit
        case response                         => IO.fail(
          InfluxException.UnexpectedResponse(
            s"Error during write [status code = ${response.code}]", response.content
          )
        )
      }

  override def exec(params: Query.Params): IO[InfluxException, Unit] =
    client.post("/query", params.toMap(), "")
      .flatMap { expectEmptyResponse }

  private def execute(params: Query.Params) =
    client.get("/query", params.toMap())

  private val expectEmptyResponse: HttpResponse.Text => IO[InfluxException, Unit] = {
    case response if response.code < 300 => IO.unit
    case response                        => IO.fail(
      InfluxException.UnexpectedResponse(
        s"Error during query [status code = ${response.code}]", response.content
      )
    )
  }
}
object LiveService {
  def layer(cfg: http.Config): Layer[Throwable, InfluxDB] =
    http.Client.layer(cfg) >>> httpLayer

  val httpLayer: ZLayer[Has[http.Client], Nothing, InfluxDB] =
    ZLayer.fromFunction { hasClient => new LiveService(hasClient.get) }
}