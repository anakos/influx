package influxdb
package manage

import cats.syntax.functor._
import influxdb.http
import influxdb.http.api
import influxdb.query.Params

object db {
  def show[E : http.Has](): RIO[E, Seq[String]] =
    query.single[E, api.SingleSeries](Params.singleQuery("SHOW DATABASES"))
      .map { result =>
        for {
          series <- result.series
          name   <- series.points("name")
        } yield name.asString.getOrElse("")
      }

  def create[E : http.Has](name: String): RIO[E, Unit] =
    exec[E, Unit](s"""CREATE DATABASE "$name"""")
      .void

  def drop[E : http.Has](name: String) =
    exec[E, Unit](s"""DROP DATABASE "$name"""")
      .void

  def exists[E : http.Has](name: String) =
    show[E]().map(_.contains(name))
}