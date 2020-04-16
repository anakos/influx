package influxdb

import atto._, Atto._
import cats.instances.all._
import cats.syntax.all._
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import influxdb.query.FieldValidator

final class Timestamp private(val unwrap: Instant)
object Timestamp {
  def validator(fieldName: String, precision: Option[Precision]): FieldValidator.Validator[Timestamp] =
    precision.fold(rfc3339UTCValidator(fieldName)) { timeValidator(fieldName, _) }
      .map { new Timestamp(_) }

  def rfc3339UTCValidator(fieldName: String): FieldValidator.Validator[Instant] =
    FieldValidator.byName(fieldName) { _.asString() }
      .flatMapF { parseRFC3339UTC(_) }

  def parseRFC3339UTC(value: String): Either[String, Instant] =
    Either.catchNonFatal(java.time.Instant.parse(value))
      .leftMap { ex => s"could not decode timestamp [$value]: ${ex.getMessage}" }

  def timeValidator(fieldName: String, precision: Precision): FieldValidator.Validator[Instant] =
    FieldValidator.byName(fieldName) { _.asNum() }
      .flatMapF { parseInstant(_, precision) }

  def parseInstant(value: BigDecimal, precision: Precision): Either[String, Instant] =
    Either.catchNonFatal {
      val timestamp = value.toLongExact
      precision match {
        case Precision.NANOSECONDS  =>
          Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(timestamp))
        case Precision.MICROSECONDS =>
          Instant.ofEpochMilli(TimeUnit.MICROSECONDS.toMillis(timestamp))
        case Precision.MILLISECONDS =>
          Instant.ofEpochMilli(timestamp)
        case Precision.SECONDS      =>
          Instant.ofEpochSecond(timestamp)
        case Precision.MINUTES      =>
          Instant.ofEpochSecond(TimeUnit.MINUTES.toSeconds(timestamp))
        case Precision.HOURS        =>
          Instant.ofEpochSecond(TimeUnit.HOURS.toSeconds(timestamp))
      }
    }
    .leftMap(ex => s"could not convert $value to long: ${ex.getMessage}")
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

sealed abstract class Precision(str: String) extends Product with Serializable {
  override def toString = str
}
object Precision {
  case object NANOSECONDS extends Precision("ns")
  case object MICROSECONDS extends Precision("u")
  case object MILLISECONDS extends Precision("ms")
  case object SECONDS extends Precision("s")
  case object MINUTES extends Precision("m")
  case object HOURS extends Precision("h")
  //case object RFC3339 extends Precision("rfc3339")
}