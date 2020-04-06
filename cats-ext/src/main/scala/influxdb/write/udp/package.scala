package influxdb
package write

import influxdb.types._

package object udp {
  def write[E](point: Point)(implicit udp: HasUdp[E]): RIO[E, Unit] =
    udp.using(_.write(point))

  def bulkWrite[E](points: Seq[Point])(implicit udp: HasUdp[E]): RIO[E, Unit] =
    udp.using(_.bulkWrite(points))
}
