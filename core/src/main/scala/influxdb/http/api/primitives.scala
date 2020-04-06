package influxdb
package http
package api

import cats._
import cats.instances.all._
import cats.syntax.option._
import cats.syntax.show._
import io.circe._

object Value {
  def num(i: BigDecimal): Value = Either3.left3(i)
  def string(s: String): Value = Either3.middle3(s)
  def bool(b: Boolean): Value = Either3.right3(b)
}

final case class Nullable(unwrap: Option[Value]) {
  def ifString[A](f: String => A): Option[A] =
    unwrap.flatMap {
      case Either3.Middle3(x) => f(x).some
      case _ => none
    }

  def asString(): Option[String] =
    ifString(identity)

  def ifBool[A](f: Boolean => A): Option[A] =
    unwrap.flatMap {
      case Either3.Right3(x) => f(x).some
      case _ => none
    }

  def asBool(): Option[Boolean] =
    ifBool(identity)

  def ifNum[A](f: BigDecimal => A): Option[A] =
    unwrap.flatMap {
      case Either3.Left3(x) => f(x).some
      case _ => none
    }

  def asNum(): Option[BigDecimal] =
    ifNum(identity)

  def ifNull[A](a: => A): Option[A] =
    unwrap.fold(a.some) { _ => none }

  def asNull(): Option[Unit] =
    ifNull(())
}
object Nullable {
  def fromNull(): Nullable = Nullable(none)
  def fromNum(i: BigDecimal): Nullable = Nullable(Value.num(i).some)
  def fromString(s: String) = Nullable(Value.string(s).some)
  def fromBool(b: Boolean) = Nullable(Value.bool(b).some)

  implicit val show: Show[Nullable] =
    Show.show { _.unwrap.fold("") { _.show } }

  implicit val decoder: Decoder[Nullable] =
    Decoder[Option[Value]].map(Nullable(_))
}