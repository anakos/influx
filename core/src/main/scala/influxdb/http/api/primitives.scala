package influxdb
package http
package api

import cats._
import cats.syntax.option._
import cats.syntax.show._
import io.circe._

sealed trait JsPrimitive extends Product with Serializable
object JsPrimitive {
  implicit val decoder: Decoder[JsPrimitive] =
    Decoder[BigDecimal].map(num(_))
        .or(Decoder[String].map(string(_)))
        .or(Decoder[Boolean].map(bool(_)))

  implicit val show: Show[JsPrimitive] =
    Show.show {
      case I(x) => x.toString()
      case S(x) => x.toString()
      case B(x) => x.toString()
    }

  final case class I private[JsPrimitive](unwrap: BigDecimal) extends JsPrimitive
  final case class S private[JsPrimitive](unwrap: String) extends JsPrimitive
  final case class B private[JsPrimitive](unwrap: Boolean) extends JsPrimitive

  def num(i: BigDecimal): JsPrimitive = new I(i)
  def string(s: String): JsPrimitive = new S(s)
  def bool(b: Boolean): JsPrimitive = new B(b)
}

final case class PrimitivePlus(unwrap: Option[JsPrimitive]) {
  def ifString[A](f: String => A): Option[A] =
    unwrap.flatMap {
      case JsPrimitive.S(x) => f(x).some
      case _ => none
    }

  def asString(): Option[String] =
    unwrap.flatMap {
      case JsPrimitive.S(x) => x.some
      case _ => none
    }

  def ifBool[A](f: Boolean => A): Option[A] =
    unwrap.flatMap {
      case JsPrimitive.B(x) => f(x).some
      case _ => none
    }

  def asBool(): Option[Boolean] =
    unwrap.flatMap {
      case JsPrimitive.B(x) => x.some
      case _ => none
    }

  def ifNum[A](f: BigDecimal => A): Option[A] =
    unwrap.flatMap {
      case JsPrimitive.I(x) => f(x).some
      case _ => none
    }

  def asNum(): Option[BigDecimal] =
    unwrap.flatMap {
      case JsPrimitive.I(x) => x.some
      case _ => none
    }

  def ifNull[A](a: => A): Option[A] =
    unwrap.fold(a.some) { _ => none }

  def asNull(): Option[Unit] =
    unwrap.fold(().some) { _ => none }
}
object PrimitivePlus {
  def fromNull(): PrimitivePlus = PrimitivePlus(none)
  def fromNum(i: BigDecimal): PrimitivePlus = PrimitivePlus(JsPrimitive.num(i).some)
  def fromString(s: String) = PrimitivePlus(JsPrimitive.string(s).some)
  def fromBool(b: Boolean) = PrimitivePlus(JsPrimitive.bool(b).some)

  implicit val show: Show[PrimitivePlus] =
    Show.show { _.unwrap.fold("") { _.show } }

  implicit val decoder: Decoder[PrimitivePlus] =
    Decoder[Option[JsPrimitive]].map(PrimitivePlus(_))
}