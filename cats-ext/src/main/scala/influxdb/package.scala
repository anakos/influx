package object influxdb {
  type Has[A]    = has.Has[http.Handle, A]
  type HasUdp[A] = has.Has[write.udp.Client, A]
  type RIO[E, A] = cats.data.ReaderT[cats.effect.IO, E, A]
}