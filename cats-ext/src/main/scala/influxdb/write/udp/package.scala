package influxdb
package write

import influxdb.types._

package object udp {
  type Has[E] = has.Has[Client, E]

  def write[E](point: Point)(implicit udp: Has[E]): RIO[E, Unit] =
    udp.using(_.write(point))

  def bulkWrite[E](points: Seq[Point])(implicit udp: Has[E]): RIO[E, Unit] =
    udp.using(_.bulkWrite(points))
}
