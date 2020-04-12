package influxdb
package http

import cats._
import cats.effect._
import cats.syntax.monadError._
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

final case class Client[F[_]](
  settings   : Config.Connect,
  // readTimeout: Option[FiniteDuration],
  backend    : SttpBackend[F, fs2.Stream[F, java.nio.ByteBuffer], WebSocketHandler]
) {
  def get(path: String, params: Map[String, String])
         (implicit F: MonadError[F, Throwable]): F[HttpResponse.Text] =
    executeRequest(
      basicRequest.get(settings.mkUri(path, params))
        .response(asStringAlways)
    )

  def getChunked(path: String, params: Map[String, String], chunkSize: query.ChunkSize)
                 (implicit F: MonadError[F, Throwable]): F[HttpResponse.Chunked[F]] =
    executeRequest(
      basicRequest.get(settings.mkUri(path, params ++ chunkSize.params()))
        .response(asStreamAlways[fs2.Stream[F, java.nio.ByteBuffer]])
    )    

  def post(path: String, params: Map[String, String], content: String)
          (implicit F: MonadError[F, Throwable]): F[HttpResponse.Text] =
    executeRequest(
      basicRequest.post(settings.mkUri(path, params))
        .body(content)
        .response(asStringAlways)
    )

  private def executeRequest[T](req: Request[T, fs2.Stream[F,java.nio.ByteBuffer]])
                               (implicit F: MonadError[F, Throwable]): F[HttpResponse[T]] =
    backend.send(req)
      .redeemWith(translateError, handleResponse)

  private def translateError[A](implicit F: MonadError[F, Throwable]): Throwable => F[A] = {
    case ex: sttp.client.SttpClientException =>
      F.raiseError(InfluxException.httpException("An error occurred during the request", ex.getCause()))
    case ex =>
      F.raiseError(InfluxException.httpException("An error occurred during the request", ex))
  }

  private def handleResponse[A](implicit F: MonadError[F, Throwable]): Response[A] => F[HttpResponse[A]] = {
    case response if response.code.code >= 400 => F.raiseError(
      InfluxException.httpException(
        s"Server answered with error code ${response.code}. Message: ${response.body}",
        response.code.code
      )
    )
    case response => F.pure(HttpResponse(response.code.code, response.body))
  }
}
object Client {
  def create(config: Config)(implicit cs: ContextShift[IO]): Resource[IO, Client[IO]] =
    AsyncHttpClientFs2Backend.resourceUsingConfig[IO](config.client.builder.build())
      .map { new Client[IO](config.connect, _) }
}

final case class HttpResponse[A](code: Int, content: A)
object HttpResponse {
  type Chunked[F[_]] = HttpResponse[fs2.Stream[F, java.nio.ByteBuffer]]
  type Text          = HttpResponse[String]
}