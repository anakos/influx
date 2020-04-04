package object influxdb {
  type RIO[E, A] = cats.data.ReaderT[cats.effect.IO, E, A]
}
