package influxdb
package write
package udp

import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress}
import cats.MonadError

final class Client[F[_] : MonadError[?[_], Throwable]] private[udp](socket: DatagramSocket, address: InetSocketAddress) {
  def write(point: Point): F[Unit] =
    send(point.serialize().getBytes)

  def bulkWrite(points: Seq[Point]) =
    send(points.map(_.serialize()).mkString("\n").getBytes)

  private def send(payload: Array[Byte]) =
    MonadError[F, Throwable].catchNonFatal {
      val packet = new DatagramPacket(payload, payload.length, address)
      socket.send(packet)
    }
}
object Client {
  final case class Config(host: String, port: Int)
}