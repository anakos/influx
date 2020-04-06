package influxdb
package write

package object udp {
  def write[E](point: Point)(implicit udp: HasUdp[E]): RIO[E, Unit] =
    udp.using(_.write(point))

  def bulkWrite[E](points: Seq[Point])(implicit udp: HasUdp[E]): RIO[E, Unit] =
    udp.using(_.bulkWrite(points))
}
