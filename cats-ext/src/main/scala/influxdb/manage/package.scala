package influxdb

import cats.effect._
import influxdb.http
import influxdb.http.HttpResponse
import influxdb.query.Params
import io.circe._

package object manage {
  def exec[E : http.Has, A : Decoder](query: String): RIO[E, influxdb.query.Result[A]] =
    http.post("/query", Params.singleQuery(query).toMap(), "")
      .flatMapF { case HttpResponse(_, content) =>
        IO.fromEither(influxdb.query.Result.extractSingle[A](content))
      }
}