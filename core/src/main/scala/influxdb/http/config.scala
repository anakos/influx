package influxdb.http

import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Realm.{AuthScheme, Builder}

final case class Config(client: Config.Client, connect: Config.Connect)
object Config {
  def defaultHttp(host: String, port: Int): Config =
    Config(
      Client.default(),
      Connect.http(host, port, None, None)
    )

  def defaultHttps(host: String, port: Int): Config =
    Config(
      Client.default(),
      Connect.https(host, port, None, None)
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

    def build() = builder.build()
  }
  object Client {
    def default(): Client = new Client(new DefaultAsyncHttpClientConfig.Builder)
  }

  sealed abstract class Connect(val host: String, val port: Int, val realm: Option[AuthRealm]) { self =>
    lazy val baseUrl: String = {
      val protocol = self match {
        case Connect.Http(_,_,_) => "http"
        case Connect.Https(_,_,_) => "https"
      }
      s"${protocol}://${host}:${port}"
    }

    lazy val authenticationRealm = self.realm.map {
      case AuthRealm(userName, password) =>
        new Builder(userName, password.getOrElse(null))
          .setUsePreemptiveAuth(true)
          .setScheme(AuthScheme.BASIC)
          .build()
    }  
  }
  object Connect {
    final case class Http(
      override val host: String,
      override val port: Int,
      override val realm: Option[AuthRealm],
    ) extends Connect(host, port, realm)

    final case class Https(
      override val host: String,
      override val port: Int,
      override val realm: Option[AuthRealm]
    ) extends Connect(host, port, realm)

    def http(host: String, port: Int, username: Option[String], password: Option[String]): Connect =
      Http(host, port, username.map { AuthRealm(_, password) })
    def https(host: String, port: Int, username: Option[String], password: Option[String]): Connect =
      Https(host, port, username.map { AuthRealm(_, password) })
  }
}

final case class AuthRealm(userName: String, password: Option[String])