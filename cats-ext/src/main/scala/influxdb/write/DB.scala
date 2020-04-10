package influxdb
package write

import cats.syntax.functor._
import cats.syntax.monadError._

import influxdb.http
import influxdb.http.HttpResponse

object DB {
  // WRITE
  def write[E : influxdb.Has](params: Params): RIO[E, Unit] = 
    http.post("/write", params.toMap(), params.points)
      .adaptError {
        case InfluxException.HttpException(msg, Some(x)) if x >= 400 && x < 500 =>
          InfluxException.ClientError(s"Error during write: $msg")
        case InfluxException.HttpException(msg, Some(x)) if x >= 500 && x < 600 =>
          InfluxException.ServerError(s"Error during write: $msg")
      }
      .reject { case HttpResponse(code, content) if code != 204 =>
        InfluxException.UnexpectedResponse(s"Error during write [status code = $code]", content)
      }
      .void
}