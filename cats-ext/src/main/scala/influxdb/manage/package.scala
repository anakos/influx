package influxdb

import cats.syntax.functor._
import influxdb.http
import influxdb.query._

package object manage {
  def exec[E : influxdb.Has](query: String): RIO[E, Unit] = {
    val params = Params.singleQuery(query)

    http.post("/query", params.toMap(), "")
      .flatMapF { influxdb.query.DB.handleResponse[Unit](params) }
      .void
  }
}