package influxdb
package query

import cats.effect._
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.monadError._
import influxdb.http
import influxdb.http.HttpResponse
import influxdb.query.{json => JSON, QueryResults}

import io.circe._

object DB {
  // QUERY
  def query_[E: influxdb.Has](params: Params): RIO[E, Unit] =
    execute[E](params)
      .flatMapF { handleResponse[Unit](params) }
      .void

  def query[E: influxdb.Has, A : QueryResults](params: Params): RIO[E, Vector[A]] =
    execute[E](params)
      .flatMapF { handleResponse[A](params) }

  private def execute[E : influxdb.Has](params: Params): RIO[E, HttpResponse.Text] =
    http.get("/query", params.toMap())
      .adaptError {
        case InfluxException.HttpException(msg, Some(x)) if x >= 400 && x < 500 =>
          InfluxException.ClientError(s"Error during query: $msg")
        case InfluxException.HttpException(msg, Some(x)) if x >= 500 && x < 600 =>
          InfluxException.ServerError(s"Error during query: $msg")
      }
  
  def handleResponse[A : QueryResults](params: => Params)(response: HttpResponse.Text) =
    IO.fromEither {
      jawn.parse(response.content)
        .leftMap { InfluxException.unexpectedResponse(params, response.content, _) }
        .flatMap { JSON.parseQueryResult[A](_) }
    }
}