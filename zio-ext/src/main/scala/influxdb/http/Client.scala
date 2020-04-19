package influxdb
package http

import org.asynchttpclient.{
  AsyncHttpClientConfig,
  DefaultAsyncHttpClientConfig,
  Realm
}
import sttp.client._
import sttp.client.asynchttpclient.ziostreams.{
  AsyncHttpClientZioStreamsBackend,
}
import zio._

final case class Client(
    settings: Config.Connect,
    backend : SttpBackend[Task, RawBytes, Nothing]
) extends HttpClient.Service[IO[InfluxException, ?], RawBytes] {
  def get(path: String, params: Map[String, String]): IO[InfluxException, HttpResponse.Text] =
    executeRequest(
      basicRequest.get(settings.mkUri(path, params))
        .response(asStringAlways)
    )

  def getChunked(path: String, params: Map[String, String], chunkSize: query.ChunkSize): IO[InfluxException, HttpResponse.Chunked[RawBytes]] =
    executeRequest(
      basicRequest.get(settings.mkUri(path, params ++ chunkSize.params()))
        .response(asStreamAlways)
    )    

  def post(path: String, params: Map[String, String], content: String): IO[InfluxException, HttpResponse.Text] =
    executeRequest(
      basicRequest.post(settings.mkUri(path, params))
        .body(content)
        .response(asStringAlways)
    )

  private def executeRequest[T](req: Request[T, RawBytes]): IO[InfluxException, HttpResponse[T]] =
    backend.send(req)
      .foldM(ex => ZIO.fail(toInfluxException(ex)), x => ZIO.fromEither(HttpResponse.fromResponse[T](x)))
}
object Client {
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
  
  def layer(cfg: influxdb.http.Config): Layer[Throwable, Has[Client]] =
    AsyncHttpClientZioStreamsBackend
      .layerUsingConfig(toAsyncHttpClientConfig(cfg))
      .map { x => Has(new Client(cfg.connect, x.get)) }
}