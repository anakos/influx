package influxdb
package http

import cats.syntax.either._

final case class HttpResponse[A](code: Int, content: A)
object HttpResponse {
  type Chunked[S] = HttpResponse[S]
  type Text       = HttpResponse[String]

  def fromResponse[A](response: sttp.client.Response[A]): Either[InfluxException, HttpResponse[A]] =
    response match  {
      case response if response.code.code >= 400 && response.code.code < 500 =>
        InfluxException.ClientError(
          s"HTTP Error [status code =  ${response.code}]. Message: ${response.body}",
        ).asLeft
      case response if response.code.code >= 500 =>
        InfluxException.ServerError(
          s"HTTP Error [status code = ${response.code}]. Message: ${response.body}",
        ).asLeft

      case response => HttpResponse(response.code.code, response.body).asRight
    }
}

object HttpClient {
  trait Service[F[_], S] {
    def get(path: String, params: Map[String, String]): F[HttpResponse.Text]
    def getChunked(path: String, params: Map[String, String], chunkSize: query.ChunkSize): F[HttpResponse.Chunked[S]]
    def post(path: String, params: Map[String, String], content: String): F[HttpResponse.Text]

    val toInfluxException: Throwable => InfluxException = {
      case ex: sttp.client.SttpClientException =>
        InfluxException.httpException("An error occurred during the request", ex.getCause())
      case ex =>
        InfluxException.httpException("An error occurred during the request", ex)
    }
  }

  import influxdb.{query => Query}
  def handleResponse[A : Query.QueryResults](params: Query.Params, response: HttpResponse.Text) =
    io.circe.jawn.parse(response.content)
      .leftMap { InfluxException.unexpectedResponse(params, response.content, _) }
      .flatMap { Query.json.parseQueryResult[A](_, params.precision) }
}