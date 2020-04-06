package influxdb
package manage

import cats.syntax.functor._

object ping {
  def apply[E : influxdb.Has](): RIO[E, Unit] =
    http.get("/ping", Map.empty)
      .void
}