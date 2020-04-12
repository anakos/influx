package influxdb

import cats.effect._
import cats.effect.syntax.bracket._
import cats.syntax.option._

import influxdb.http
import influxdb.manage.db

import org.specs2.execute._
import org.specs2.matcher.IOMatchers

trait InfluxDbContext[Env] extends org.specs2.specification.ForEach[Env] with IOMatchers {
  def dbName: String

  def env: cats.effect.Resource[IO, Env]

  def defaultConfig(): http.Config =
    http.Config(
      http.Config.Client.default().setRealm("influx_user".some, "influx_password".some),
      http.Config.Connect.Http("localhost", 8086)
    )

  def withDb[A](action: RIO[Env, A])(implicit has: influxdb.Has[Env]): RIO[Env, A] =
    db.create[Env](dbName).bracket(_ => action) { _ => db.drop[Env](dbName) }

  override def foreach[R: AsResult](f: Env => R): Result =
    env.use { x => IO(AsResult(f(x))) }
      .unsafeRunSync()
}