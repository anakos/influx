package influxdb.http

import cats.syntax.option._
import sttp.client._
import sttp.model._
import sttp.model.Uri.UserInfo

final case class Config(client: Config.Client, connect: Config.Connect)
object Config {
  def defaultHttp(host: String, port: Int): Config =
    Config(Client.default(), Connect.Http(host, port))

  def defaultHttps(host: String, port: Int): Config =
    Config(Client.default(), Connect.Https(host, port))

  final case class Client private[http](connectTimeout      : Option[Int],
                                        requestTimeout      : Option[Int],
                                        acceptAnyCertificate: Boolean,
                                        creds               : Option[sttp.model.Uri.UserInfo]) {
    def setConnectTimeout(timeout: Int): Client =
      this.copy(connectTimeout = timeout.some)

    def setRequestTimeout(timeout: Int): Client =
      this.copy(requestTimeout = timeout.some)

    def setAcceptAnyCertificate(acceptAnyCertificate: Boolean): Client =
      this.copy(acceptAnyCertificate = acceptAnyCertificate)

    def setUserInfo(userName: String, password: Option[String]) =
      this.copy(creds = UserInfo(userName, password).some)
  }
  object Client {
    def default(): Client = Client(none, none, false, none)
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