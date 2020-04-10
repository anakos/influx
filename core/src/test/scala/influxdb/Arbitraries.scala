package influxdb

import cats.syntax.apply._
import org.scalacheck._
import org.scalacheck.cats.implicits._

object Arbitraries {
  implicit val arbNatural: Arbitrary[Natural] =
    Arbitrary(
      Gen.posNum[Long].map(Natural.create(_).get)
    )

  final case class ValidDurationUnit(unwrap: String)
  object ValidDurationUnit {
    implicit val arbitrary: Arbitrary[ValidDurationUnit] =
      Arbitrary(
        Gen.oneOf[String](DurationLiteral.conversions.keys)
          .map { ValidDurationUnit(_) }
      )
  }

  final case class InvalidDurationUnit(unwrap: String)
  object InvalidDurationUnit {
    implicit val arbitrary: Arbitrary[InvalidDurationUnit] =
      Arbitrary(
        Gen.alphaNumStr
          .suchThat(DurationLiteral.conversions.get(_).isEmpty)
          .map { InvalidDurationUnit(_) }
      )
  }

  final case class ValidDurationString(value: Natural, unit: String) {
    override def toString(): String = s"${value}$unit"
  }
  object ValidDurationString {
    implicit def arb: Arbitrary[ValidDurationString] =
      Arbitrary(
        (Arbitrary.arbitrary[Natural],
          Arbitrary.arbitrary[ValidDurationUnit]).mapN { (nat, unit) =>
            ValidDurationString(nat, unit.unwrap)
          }
      )
  }
}

