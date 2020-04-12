package object influxdb {
  import cats.effect.IO

  type RawBytes   = fs2.Stream[IO, java.nio.ByteBuffer]
  type HttpClient = http.Client[IO, RawBytes]
  type Has[A]     = has.Has[HttpClient, A]
  type UdpClient  = write.udp.Client[IO]
  type HasUdp[A]  = has.Has[UdpClient, A]
  type RIO[E, A]  = cats.data.ReaderT[cats.effect.IO, E, A]
}