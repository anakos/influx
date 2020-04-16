package influxdb
package query

import cats.effect._
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.monadError._
import fs2.{Chunk, Stream}
import influxdb.http
import influxdb.http.HttpResponse
import influxdb.query.{json => JSON, QueryResults}

import io.circe._
import io.circe.{fs2 => StreamingParser}

object DB {
  // QUERY
  def query_[E: influxdb.Has](params: Params): RIO[E, Unit] =
    execute[E](params)
      .flatMapF { handleResponse[Unit](params) }
      .void

  def query[E: influxdb.Has, A : QueryResults](params: Params): RIO[E, Vector[A]] =
    execute[E](params)
      .flatMapF { handleResponse[A](params) }

  def queryChunked[E: influxdb.Has, A : QueryResults](params: Params, chunkSize: ChunkSize): Stream[RIO[E, ?], A] =
    Stream.eval(executeChunked[E, A](params, chunkSize))
      .flatMap {
        _.content
         .map { x => Chunk.byteBuffer(x) }
         .through(StreamingParser.byteArrayParserC[IO])
         .evalMap(js => IO.fromEither(JSON.parseQueryResult[A](js, params.precision)))
         .flatMap(Stream.emits(_))
         .translate(LiftIO.liftK[RIO[E, ?]])
      }
  
  private def executeChunked[E : influxdb.Has, A](params: Params, chunkSize: ChunkSize): RIO[E, HttpResponse.Chunked[RawBytes]] =
    withErrorHandling(http.getChunked[E]("/query", params.toMap(), chunkSize))

  private def execute[E : influxdb.Has](params: Params): RIO[E, HttpResponse.Text] =
    withErrorHandling(http.get("/query", params.toMap()))
      
  def handleResponse[A : QueryResults](params: => Params)(response: HttpResponse.Text) =
    IO.fromEither {
      jawn.parse(response.content)
        .leftMap { InfluxException.unexpectedResponse(params, response.content, _) }
        .flatMap { JSON.parseQueryResult[A](_, params.precision) }
    }

  private def withErrorHandling[E, A]: RIO[E, A] => RIO[E, A] =
    _.adaptError {
      case InfluxException.HttpException(msg, Some(x)) if x >= 400 && x < 500 =>
        InfluxException.ClientError(s"Error during query: $msg")
      case InfluxException.HttpException(msg, Some(x)) if x >= 500 && x < 600 =>
        InfluxException.ServerError(s"Error during query: $msg")
    }
}