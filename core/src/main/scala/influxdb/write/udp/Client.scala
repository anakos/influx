package influxdb
package write
package udp

import cats.effect._
import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress}

final class Client private[udp](socket: DatagramSocket, address: InetSocketAddress) {
  def write(point: Point): IO[Unit] =
    send(point.serialize().getBytes)

  def bulkWrite(points: Seq[Point]) =
    send(points.map(_.serialize()).mkString("\n").getBytes)

  private def send(payload: Array[Byte]) = IO {
    val packet = new DatagramPacket(payload, payload.length, address)
    socket.send(packet)
  }
}
object Client {
  final case class Config(host: String, port: Int)

  def create(config: Config): Resource[IO, Client] =
    Resource.make(IO { new DatagramSocket() })(socket => IO { socket.close() })
      .map { new Client(_, new InetSocketAddress(config.host, config.port) )}
}