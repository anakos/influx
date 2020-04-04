package influxdb.types

import cats._
import cats.syntax.eq._
import cats.syntax.show._

/**
 * This code was ported from scalaz using this version from scalaz:
 * https://github.com/scalaz/scalaz/blob/5633bdf21b42013fcf3b4431d352954df345d0b4/core/src/main/scala/scalaz/Either3.scala
 *
 */
sealed abstract class Either3[A, B, C] extends Product with Serializable {
  def fold[Z](left: A => Z, middle: B => Z, right: C => Z): Z = this match {
    case Either3.Left3(a)   => left(a)
    case Either3.Middle3(b) => middle(b)
    case Either3.Right3(c)  => right(c)
  }

  def leftOr[Z](z: => Z)(f: A => Z)   = fold(f, _ => z, _ => z)
  def middleOr[Z](z: => Z)(f: B => Z) = fold(_ => z, f, _ => z)
  def rightOr[Z](z: => Z)(f: C => Z)  = fold(_ => z, _ => z, f)
}
object Either3 {
  implicit def show[A: Show, B: Show, C: Show]: Show[Either3[A, B, C]] =
    new Show[Either3[A, B, C]] {
      override def show(v: Either3[A, B, C]) =
        v.fold(_.show, _.show, _.show)
    }
  implicit def eqv[A: Eq, B: Eq, C: Eq]: Eq[Either3[A, B, C]] =
    new Eq[Either3[A, B, C]] {
      override def eqv(e1: Either3[A, B, C], e2: Either3[A, B, C]) =
        (e1, e2) match {
          case (Left3(a1),   Left3(a2))   => a1 === a2
          case (Middle3(b1), Middle3(b2)) => b1 === b2
          case (Right3(c1),  Right3(c2))  => c1 === c2
          case _ => false
        }      
    }

  final case class Left3[A, B, C](a: A) extends Either3[A, B, C]
  final case class Middle3[A, B, C](b: B) extends Either3[A, B, C]
  final case class Right3[A, B, C](c: C) extends Either3[A, B, C]

  def left3[A, B, C](a: A):   Either3[A, B, C] = Left3(a)
  def middle3[A, B, C](b: B): Either3[A, B, C] = Middle3(b)
  def right3[A, B, C](c: C):  Either3[A, B, C] = Right3(c)
}
