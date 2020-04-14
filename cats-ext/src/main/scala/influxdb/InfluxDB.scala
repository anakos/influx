package influxdb

import cats.effect._
import fs2.Stream
import influxdb.http.{Client, Config}
import org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClientConfig}
import org.asynchttpclient.Realm
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

object InfluxDB {
  def create(config: Config)(implicit cs: ContextShift[IO]): Resource[IO, HttpClient] = {
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
      .map { new Client[IO, Stream[IO, java.nio.ByteBuffer]](config.connect, _) }
    }
}