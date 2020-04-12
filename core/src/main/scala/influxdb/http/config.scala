package influxdb.http

import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Realm.{AuthScheme, Builder}
import sttp.client._
import sttp.model._

final case class Config(client: Config.Client, connect: Config.Connect)
object Config {
  def defaultHttp(host: String, port: Int): Config =
    Config(Client.default(), Connect.Http(host, port)
      // Connect.http(host, port, None, None)
    )

  def defaultHttps(host: String, port: Int): Config =
    Config(Client.default(), Connect.Https(host, port)
      // Connect.https(host, port, None, None)
    )

  final class Client private[http](val builder: DefaultAsyncHttpClientConfig.Builder) {
    def setConnectTimeout(timeout: Int) = {
      builder.setConnectTimeout(timeout)
      this
    }

    def setRequestTimeout(timeout: Int) = {
      builder.setRequestTimeout(timeout)
      this
    }

    def setAcceptAnyCertificate(acceptAnyCertificate: Boolean) = {
      builder.setUseInsecureTrustManager(acceptAnyCertificate)
      this
    }

    def setRealm(username: Option[String], password: Option[String]) = {
      username.foreach { u =>
        builder.setRealm(
          new Builder(u, password.getOrElse(null))
            .setUsePreemptiveAuth(true)
            .setScheme(AuthScheme.BASIC)
            .build()          
        )
      }
      this
    }

    def build() = builder.build()
  }
  object Client {
    def default(): Client = new Client(new DefaultAsyncHttpClientConfig.Builder)
  }

  sealed abstract class Connect(val host: String, val port: Int /*, val realm: Option[AuthRealm] */) { self =>
    private lazy val protocol = self match {
      case Connect.Http(_,_) => "http"
      case Connect.Https(_,_) => "https"
    }

    def mkUri(path: String, params: Map[String, String]): sttp.model.Uri = {
      val paths   = path.split("/").filter(_.nonEmpty).toList
      val qParams = params.toList.map { case (k,v) =>
        Uri.QuerySegment.KeyValue(k, java.net.URLEncoder.encode(v, "UTF-8"), Uri.QuerySegmentEncoding.Standard, identity)
      }

      uri"${protocol}://${host}:${port}/${paths}"
        .copy(querySegments = qParams)
    }
  }
  object Connect {
    final case class Http(
      override val host: String,
      override val port: Int,
      // override val realm: Option[AuthRealm],
    ) extends Connect(host, port)

    final case class Https(
      override val host: String,
      override val port: Int,
      // override val realm: Option[AuthRealm]
    ) extends Connect(host, port)

    // def http(host: String, port: Int, username: Option[String], password: Option[String]): Connect =
    //   Http(host, port, username.map { AuthRealm(_, password) })
    // def https(host: String, port: Int, username: Option[String], password: Option[String]): Connect =
    //   Https(host, port, username.map { AuthRealm(_, password) } )
  }
}

final case class AuthRealm(userName: String, password: Option[String])