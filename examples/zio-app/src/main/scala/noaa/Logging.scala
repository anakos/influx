package noaa

import zio._

object Logging {
  trait Service {
    def debug(msg: String): Task[Unit]
    def info(msg: String): Task[Unit]
    def warn(msg: String): Task[Unit]
    def error(msg: String, ex: Throwable): Task[Unit]
  }

  def debug(msg: String): RIO[Logging, Unit] =
    ZIO.accessM(_.get.debug(msg))

  def info(msg: String): RIO[Logging, Unit] =
    ZIO.accessM(_.get.info(msg))

  def warn(msg: String): RIO[Logging, Unit]  =
    ZIO.accessM(_.get.warn(msg))

  def error(msg: String, ex: Throwable): RIO[Logging, Unit] =
    ZIO.accessM(_.get.error(msg, ex))

  val any: ZLayer[Logging, Nothing, Logging] =
    ZLayer.requires[Logging]

  val slf4j: Layer[Throwable, Logging] =
    ZLayer.succeed(
      new Service {
        val logger = org.slf4j.LoggerFactory.getLogger("influxdb-examples")

        def debug(msg: String): UIO[Unit] =
          ZIO.succeed(logger.debug(msg))
        def info(msg: String): UIO[Unit] =
          ZIO.succeed(logger.info(msg))
        def warn(msg: String): UIO[Unit] =
          ZIO.succeed(logger.warn(msg))
        def error(msg: String, ex: Throwable): UIO[Unit] =
          ZIO.succeed(logger.error(msg, ex))
      }
    )
}