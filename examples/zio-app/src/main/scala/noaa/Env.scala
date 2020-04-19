package noaa

import cats.syntax.option._
import influxdb._
import influxdb.http.Config
import zio._

object Env {
  def create(host: String, port: Int, username: String, password: String): Layer[Throwable, ZEnv with InfluxDB with Logging] =
    influxdb.LiveService.layer(
      Config(Config.Client.default().setUserInfo(username, password.some), Config.Connect.Http(host, port))       
    ) ++ Logging.slf4j ++ ZEnv.live
}