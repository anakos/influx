package influxdb

import cats.syntax.option._

sealed abstract class InfluxException(msg: String) extends Exception(msg) with Product with Serializable
object InfluxException {
  /** This branch can represent error conditions returned from InfluxDB directly */
  final case class ServerError(msg: String) extends InfluxException(msg)
  final case class ClientError(msg: String) extends InfluxException(msg)
  /** This branch represents json that cannot be deserialized properly or weird response codes */
  final case class UnexpectedResponse(msg: String, content: String) extends InfluxException(msg)
  final case class HttpException(msg: String, code: Option[Int]) extends InfluxException(msg)

  def httpException(msg: String, code: Int): InfluxException =
    HttpException(msg, code.some)
  def httpException(msg: String, underlying: Throwable): InfluxException =
    withUnderlying(HttpException(msg, none), underlying)
        
  def withUnderlying[A <: Throwable](a: A, underlying: Throwable): A =
    a.initCause(underlying)
      .asInstanceOf[A]    
}