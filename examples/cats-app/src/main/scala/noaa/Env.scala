package noaa

import cats.effect._
import cats.syntax.option._
import influxdb.http.{Client, Config}
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext

final case class Env(
  influx : Client,
  logger : org.slf4j.Logger,
  blocker: cats.effect.Blocker,
  cs     : ContextShift[IO],
  timer  : Timer[IO]
)
object Env {
  implicit val logger: Logger.Has[Env] =
    has.Has.mk(_.logger)

  implicit val influx: influxdb.Has[Env] =
    has.Has.mk(_.influx)

  implicit val blocker: has.Has[Blocker, Env] =
    has.Has.mk(_.blocker)

  implicit val contextShift: has.Has[ContextShift[IO], Env] =
    has.Has.mk(_.cs)

  implicit val timer: has.Has[Timer[IO], Env] =
    has.Has.mk(_.timer)

  def create(host: String, port: Int, username: String, password: String): Resource[IO, Env] =
    for {
      influx  <- Client.create(
        Config(
          Config.Client.default().setUserInfo(username, password.some),
          Config.Connect.Http(host, port)
        )        
      )(IO.contextShift(ExecutionContext.global))

      logger  <- Resource.liftF(IO { LoggerFactory.getLogger("influxdb-examples") })

      blocker <- Resource[IO, ExecutionContext](
        IO {
          val executor = java.util.concurrent.Executors.newCachedThreadPool()
          val ec = ExecutionContext.fromExecutor(executor)
          (ec, IO(executor.shutdown()))
        }
      )
    } yield Env(influx,
                logger,
                Blocker.liftExecutionContext(blocker),
                IO.contextShift(ExecutionContext.global), 
                IO.timer(ExecutionContext.global))
}