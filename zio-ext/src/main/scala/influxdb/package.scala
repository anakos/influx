package object influxdb {
  import zio._
  import zio.stream.Stream

  type InfluxDB = Has[InfluxDB.Service]
  type RawBytes = Stream[Throwable, java.nio.ByteBuffer]
}