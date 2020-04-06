package influxdb

import cats.effect._
import cats.syntax.monadError._
import influxdb.http
import influxdb.http.HttpResponse
import io.circe._

package object query {
  def series[E : influxdb.Has](params: Params): RIO[E, Result[http.api.SingleSeries]] =
    single[E, http.api.SingleSeries](params)

  def single[E : influxdb.Has, A : Decoder](params: Params): RIO[E, Result[A]] =
    execute(params).flatMapF { case HttpResponse(_, content) =>
      IO.fromEither(Result.extractSingle[A](content))
    }

  def mutliSeries[E : influxdb.Has](params: Params): RIO[E, List[Result[http.api.SingleSeries]]] =
    multi[E, http.api.SingleSeries](params)

  def multi[E : influxdb.Has, A : Decoder](params: Params): RIO[E, List[Result[A]]] =
    execute(params).flatMapF { case HttpResponse(_, content) =>
      IO.fromEither(Result.extract[A](content))
    }

  private def execute[E : influxdb.Has](params: Params): RIO[E, HttpResponse] =
    http.get("/query", params.toMap())
      .adaptError {
        case InfluxException.HttpException(msg, Some(x)) if x >= 400 && x < 500 =>
          InfluxException.ClientError(s"Error during query: $msg")
        case InfluxException.HttpException(msg, Some(x)) if x >= 500 && x < 600 =>
          InfluxException.ServerError(s"Error during query: $msg")
      }
}