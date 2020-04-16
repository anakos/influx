package influxdb

import cats.instances.all._
import cats.syntax.all._
import org.specs2.mutable
import scala.concurrent.duration._
import java.time._

class DurationParserSpec extends mutable.Spec with org.specs2.ScalaCheck {
  import Arbitraries._

  "DurationLiteral.parse" >> {
    "extracts duration from valid strings" >> prop { valid: ValidDurationString =>
      DurationLiteral.parse(valid.toString()) must beRight
    }

    "fails on malformed input duration from valid strings" >> prop { invalid: InvalidDurationUnit =>
      DurationLiteral.parse(invalid.toString()) must beLeft
    }

    "sums multiple values with white space" >> {
      DurationLiteral.parse("168h 12m 30s 102ns") must beRight.like {
        case result =>
          result.unwrap must_=== 168.hours.combine(12.minutes).combine(30.seconds).combine(102.nanos)
      }
    }

    "sums multiple values without white space" >> {
      DurationLiteral.parse("168h12m30s102ns") must beRight.like {
        case result =>
          result.unwrap must_=== 168.hours.combine(12.minutes).combine(30.seconds).combine(102.nanos)
      }
    }

  }

  "Timestamp" >> {
    "can be parsed from valid strings" >> {
      Timestamp.parseRFC3339UTC("2016-11-01T20:44:39Z") must beRight
    }

    "can be validated from a timestamp" >> {
      val now      = Instant.ofEpochMilli(1587041691151L)
      val nowNanos = now.getEpochSecond(). * (scala.math.pow(10, 9)) + now.getNano().toLong
      (Timestamp.parseInstant(BigDecimal(nowNanos), Precision.NANOSECONDS),
        Timestamp.parseInstant(BigDecimal(1587041691151L), Precision.MILLISECONDS))
        .mapN { (nanoTime, milliTime) =>
          (LocalDateTime.ofInstant(nanoTime, ZoneOffset.UTC), LocalDateTime.ofInstant(milliTime, ZoneOffset.UTC))
        } must beRight.like {
          case (nanoTime, milliTime) =>
            nanoTime.getMonth() must_== Month.APRIL
            milliTime.getMonth() must_== Month.APRIL
            nanoTime.getDayOfMonth() must_== 16
            milliTime.getDayOfMonth() must_== 16
            nanoTime.getYear() must_== 2020
            milliTime.getYear() must_== 2020
            nanoTime.getHour() must_== milliTime.getHour()
            nanoTime.getMinute() must_== milliTime.getMinute()
            nanoTime.getSecond() must_== milliTime.getSecond()
        }
    }
  }
}