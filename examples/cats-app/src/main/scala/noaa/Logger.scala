package noaa

import cats.effect._

object Logger {
  type Has[A] = has.Has[org.slf4j.Logger, A]

  def debug[E](msg: String)(implicit has: Has[E]) =
    has.using(l => IO { l.debug(msg) })

  def info[E](msg: String)(implicit has: Has[E]) =
    has.using(l => IO { l.info(msg) })

  def warn[E](msg: String)(implicit has: Has[E]) =
    has.using(l => IO { l.warn(msg) })

  def error[E](msg: String, ex: Throwable)(implicit has: Has[E]) =
    has.using(l => IO { l.error(msg, ex) })
}