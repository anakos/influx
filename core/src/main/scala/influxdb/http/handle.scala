package influxdb
package http

import cats.effect._
import cats.syntax.either._
import java.nio.charset.Charset
import org.asynchttpclient._
import scala.jdk.CollectionConverters._

final class Handle(client: AsyncHttpClient, settings: Config.Connect) {
  def get(path: String, params: Map[String, String]): IO[HttpResponse] =
    makeRequest(
      withParams(params)(
        withRealm(client.prepareGet(s"${settings.baseUrl}${path}"))
      )
    )

  def post(path: String, params: Map[String, String], content: String): IO[HttpResponse] =
    makeRequest(
      withParams(params)(
        withRealm(client.preparePost(s"${settings.baseUrl}${path}"))
          .setBody(content)
          .setCharset(Charset.forName("UTF-8"))
      )
    )  

  private def withRealm(requestBuilder: BoundRequestBuilder): BoundRequestBuilder =
    settings.authenticationRealm.fold(requestBuilder) { requestBuilder.setRealm(_) }

  private def withParams(params: Map[String, String]): BoundRequestBuilder => BoundRequestBuilder =
    _.setQueryParams(params.map(p => new Param(p._1, p._2)).toList.asJava)

  private def makeRequest(requestBuilder: BoundRequestBuilder): IO[HttpResponse] =
    IO.async { cb =>
      requestBuilder.execute(new AsyncCompletionHandler[Response] {
        override def onCompleted(response: Response): Response = {
          if (response.getStatusCode() >= 400) cb(
            InfluxException.httpException(
              s"Server answered with error code ${response.getStatusCode}. Message: ${response.getResponseBody}",
              response.getStatusCode()
            ).asLeft
          )
          else cb(
            HttpResponse(response.getStatusCode, response.getResponseBody).asRight
          )
          response
        }
        override def onThrowable(ex: Throwable) =
          cb(InfluxException.httpException("An error occurred during the request", ex).asLeft)
      })
      ()
    }
}
object Handle {
  def create(config: Config): Resource[IO, Handle] =
    Resource.make(IO { new DefaultAsyncHttpClient(config.client.build()) })(x => IO { x.close() })
      .map { new Handle(_, config.connect) }
}

final case class HttpResponse(code: Int, content: String)