package influxdb
package manage

import cats.syntax.functor._
import influxdb.query.Params

object db {
  def show[E : influxdb.Has](): RIO[E, Seq[String]] =
    query.series[E](Params.singleQuery("SHOW DATABASES"))
      .map { result =>
        for {
          series <- result.series
          name   <- series.points("name")
        } yield name.asString.getOrElse("")
      }

  def create[E : influxdb.Has](name: String): RIO[E, Unit] =
    exec[E, Unit](s"""CREATE DATABASE "$name"""")
      .void

  def drop[E : influxdb.Has](name: String) =
    exec[E, Unit](s"""DROP DATABASE "$name"""")
      .void

  def exists[E : influxdb.Has](name: String) =
    show[E]().map(_.contains(name))
}