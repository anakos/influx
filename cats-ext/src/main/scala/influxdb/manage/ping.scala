package influxdb
package manage

import cats.syntax.functor._
import influxdb.http

object ping {
  def apply[E : http.Has](): RIO[E, Unit] =
    http.get("/ping", Map.empty)
      .void
}