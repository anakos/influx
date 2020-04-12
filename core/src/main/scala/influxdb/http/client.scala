package influxdb
package http

import sttp.client._
import sttp.client.monad.{MonadError => SttpMonadError}
import sttp.client.monad.syntax._

final case class Client[F[_], S](settings: Config.Connect, backend : SttpBackend[F, S, Nothing]) {
  def get(path: String, params: Map[String, String])
         (implicit F: SttpMonadError[F]): F[HttpResponse.Text] =
    executeRequest(
      basicRequest.get(settings.mkUri(path, params))
        .response(asStringAlways)
    )

  def getChunked(path: String, params: Map[String, String], chunkSize: query.ChunkSize)
                 (implicit F: SttpMonadError[F]): F[HttpResponse.Chunked[S]] =
    executeRequest(
      basicRequest.get(settings.mkUri(path, params ++ chunkSize.params()))
        .response(asStreamAlways)
    )    

  def post(path: String, params: Map[String, String], content: String)
          (implicit F: SttpMonadError[F]): F[HttpResponse.Text] =
    executeRequest(
      basicRequest.post(settings.mkUri(path, params))
        .body(content)
        .response(asStringAlways)
    )

  private def executeRequest[T](req: Request[T, S])
                               (implicit F: SttpMonadError[F]): F[HttpResponse[T]] =
    backend.send(req)
      .handleError {
        case ex: sttp.client.SttpClientException =>
          F.error(InfluxException.httpException("An error occurred during the request", ex.getCause()))
        case ex =>
          F.error(InfluxException.httpException("An error occurred during the request", ex))
      }
      .flatMap {
        case response if response.code.code >= 400 => F.error(
          InfluxException.httpException(
            s"Server answered with error code ${response.code}. Message: ${response.body}",
            response.code.code
          )
        )
        case response => F.eval(HttpResponse(response.code.code, response.body))
      }
}

final case class HttpResponse[A](code: Int, content: A)
object HttpResponse {
  type Chunked[S] = HttpResponse[S]
  type Text       = HttpResponse[String]
}