package object influxdb {
  type HttpClient = http.Client[cats.effect.IO]
  type Has[A]     = has.Has[HttpClient, A]
  type HasUdp[A]  = has.Has[write.udp.Client, A]
  type RIO[E, A]  = cats.data.ReaderT[cats.effect.IO, E, A]
}