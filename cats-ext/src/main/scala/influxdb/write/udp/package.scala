package influxdb
package write

import cats.effect._
import java.net.{DatagramSocket, InetSocketAddress}

package object udp {
  def create(config: Client.Config): Resource[IO, UdpClient] =
    Resource.make(IO { new DatagramSocket() })(socket => IO { socket.close() })
      .map { new Client[IO](_, new InetSocketAddress(config.host, config.port) )} 
}