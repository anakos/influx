package influxdb

import atto._, Atto._
import cats.instances.all._
import cats.syntax.all._
import java.time.Instant
import scala.concurrent.duration._
import influxdb.query.FieldValidator


// TODO: revisit this. it would be gereat to be able to have a type parameter handle the dispatch of the correct precision parser
final class Timestamp private(val unwrap: Instant)
object Timestamp {
  def validator(fieldName: String): FieldValidator.Validator[Timestamp] =
    epochMillisValidator(fieldName).orElse(rfc3339UTCValidator(fieldName))
      .map { new Timestamp(_) }

  def epochMillisValidator(fieldName: String): FieldValidator.Validator[Instant] =
    FieldValidator.byName(fieldName) { _.asNum() }
      .flatMapF { toEpochMillis(_) }

  def rfc3339UTCValidator(fieldName: String): FieldValidator.Validator[Instant] =
    FieldValidator.byName(fieldName) { _.asString() }
      .flatMapF { parseRFC3339UTC(_) }

  def parseRFC3339UTC(value: String): Either[String, Instant] =
    Either.catchNonFatal(java.time.Instant.parse(value))
      .leftMap { ex => s"could not decode timestamp [$value]: ${ex.getMessage}" }
  
  def toEpochMillis(value: BigDecimal): Either[String, Instant] =
    Either.catchNonFatal(Instant.ofEpochMilli(value.toLongExact))
      .leftMap { ex => s"could not convert $value to long: ${ex.getMessage}" }
}

final class Natural private(val value: Long) {
  override def toString(): String =
    value.toString()
}
object Natural {
  val parser = long.flatMap { x =>
    create(x)
      .fold[Parser[Natural]](err(s"$x < 0")) { ok(_) }
  }
  def create(num: Long): Option[Natural] =
    if (num < 0) None
    else Some(new Natural(num))
}

final class DurationLiteral private(val unwrap: FiniteDuration)
object DurationLiteral {
  def validator(fieldName: String): FieldValidator.Validator[FiniteDuration] =
    FieldValidator.byName(fieldName) { _.asString() }
      .flatMapF { parse(_).map(_.unwrap) }

  val conversions: Map[String, Natural => FiniteDuration] =
    Map[String, Natural => FiniteDuration](
      ("ns",  _.value.nanoseconds),
      ("u",  _.value.microseconds),
      ("µ",  _.value.microseconds),
      ("ms",  _.value.milliseconds),
      ("s",  _.value.seconds),
      ("m",  _.value.minutes),
      ("h",  _.value.hours),
      ("d",  _.value.days),
      ("w",  (n: Natural) => (n.value * 7).days)
    )

  val unitParser: Parser[Natural => FiniteDuration] =
    (string("ns") | string("ms") | stringOf1(char('w')) |
      stringOf1(char('u')) | stringOf1(char('µ')) |
      stringOf1(char('s')) | stringOf1(char('m')) |
      stringOf1(char('h')) | stringOf1(char('d'))).flatMap { x =>
        conversions.get(x)
          .fold[Parser[Natural => FiniteDuration]](err(s"unknown duration unit: $x")) { ok(_) }
    }

  def parse(value: String): Either[String, DurationLiteral] =
    (sepBy((Natural.parser, unitParser).mapN { (nat, fn) => fn(nat) }, opt(whitespace)) <~ endOfInput)
      .parseOnly(value.trim())
      .either
      .bimap(
        ex => s"$value could not be parsed as a valid duration: $ex",
        x => new DurationLiteral(x.combineAll)
      )
}