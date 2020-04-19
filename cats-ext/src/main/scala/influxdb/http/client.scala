package influxdb
package http

import cats.effect._
import sttp.client._
import org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClientConfig}
import org.asynchttpclient.Realm
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

final case class Client(
    settings: Config.Connect,
    backend : SttpBackend[IO, RawBytes, Nothing]
) extends HttpClient.Service[IO, RawBytes] {
  def get(path: String, params: Map[String, String]): IO[HttpResponse.Text] =
    executeRequest(
      basicRequest.get(settings.mkUri(path, params))
        .response(asStringAlways)
    )

  def getChunked(path: String, params: Map[String, String], chunkSize: query.ChunkSize): IO[HttpResponse.Chunked[RawBytes]] =
    executeRequest(
      basicRequest.get(settings.mkUri(path, params ++ chunkSize.params()))
        .response(asStreamAlways)
    )    

  def post(path: String, params: Map[String, String], content: String): IO[HttpResponse.Text] =
    executeRequest(
      basicRequest.post(settings.mkUri(path, params))
        .body(content)
        .response(asStringAlways)
    )

  private def executeRequest[T](req: Request[T, RawBytes]): IO[HttpResponse[T]] =
    backend.send(req)
      .redeemWith(
        ex => IO.raiseError(toInfluxException(ex)),
        x => IO.fromEither(HttpResponse.fromResponse(x))
      )
}
object Client {
  def create(config: Config)(implicit cs: ContextShift[IO]): Resource[IO, Client] = {
    val toAsyncHttpClientConfig: Config => AsyncHttpClientConfig = {
      case Config(Config.Client(connect, req, acceptAnyTls, creds), _) =>
        val builder = new DefaultAsyncHttpClientConfig.Builder
        req.foreach { builder.setRequestTimeout(_) }
        connect.foreach { builder.setConnectTimeout(_) }
        builder.setUseInsecureTrustManager(acceptAnyTls)
        creds.foreach { c =>
          builder.setRealm(
            new Realm.Builder(c.username, c.password.getOrElse(null))
              .setUsePreemptiveAuth(true)
              .setScheme(Realm.AuthScheme.BASIC)
              .build()          
          )
        }
        builder.build()
    }

    AsyncHttpClientFs2Backend
      .resourceUsingConfig[IO](toAsyncHttpClientConfig(config))
      .map { new Client(config.connect, _) }
  }    
}