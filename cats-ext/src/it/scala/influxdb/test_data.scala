package influxdb

import cats.instances.either._
import cats.syntax.apply._
import cats.syntax.either._

import influxdb.query._
import influxdb.query.types._

import java.time.Instant

import scala.collection.immutable.ListMap

final case class TestMeasurement(time: Instant, value: Int)
object TestMeasurement {
  implicit val parser: QueryResults[TestMeasurement] =
    new QueryResults[TestMeasurement] {
      def parseWith(precision: Option[Precision],
                    name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, TestMeasurement] =
        (Timestamp.validator("time", precision: Option[Precision]),
          FieldValidator.byName("value") { _.asNum() }
            .flatMapF(x => Either.catchNonFatal(x.toIntExact).leftMap { _ => s"too big for an int: $x"}))
          .mapN((ts, value) => TestMeasurement(ts.unwrap, value))
          .run(data)
    }
}

final case class MultiQueryExample(unwrap: Either[SubscriberInternal, WriteInternal])
object MultiQueryExample {
  implicit val parser: QueryResults[MultiQueryExample] =
    new QueryResults[MultiQueryExample] {
      def parseWith(precision: Option[Precision],
                    name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, MultiQueryExample] =
        QueryResults[SubscriberInternal]
          .parseWith(precision, name, tags, data)
          .map(_.asLeft)
          .orElse(
            QueryResults[WriteInternal]
              .parseWith(precision, name, tags, data)
              .map(_.asRight)
          )
          .map(MultiQueryExample(_))
    }
}
final case class SubscriberInternal(
    time: Instant,
    createFailures: Long,
    hostname: String,
    pointsWritten: Long,
    writeFailures: Long
)
object SubscriberInternal {
  implicit val parser: QueryResults[SubscriberInternal] =
    new QueryResults[SubscriberInternal] {
      def parseWith(precision: Option[Precision],
                    name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, SubscriberInternal] =
        (Timestamp.validator("time", precision).map(_.unwrap),
          FieldValidator.byName("createFailures") { _.asLong() },
          FieldValidator.byName("hostname") { _.asString() },
          FieldValidator.byName("pointsWritten") { _.asLong() },
          FieldValidator.byName("writeFailures") { _.asLong() })
          .mapN(SubscriberInternal(_,_,_,_,_))
          .run(data)
    }
}

final case class WriteInternal(
    time: Instant,
    hostname: String,
    pointReq: Long,
    pointReqLocal: Long,
    req: Long,
    subWriteDrop: Long,
    subWriteOk: Long,
    writeDrop: Long,
    writeError: Long,
    writeOk: Long,
    writeTimeout: Long
)
object WriteInternal {
  implicit val parser: QueryResults[WriteInternal] =
    new QueryResults[WriteInternal] {
      def parseWith(precision: Option[Precision],
                    name: Option[String],
                    tags: ListMap[String, Value],
                    data: ListMap[String, Nullable]): Either[String, WriteInternal] =
        (Timestamp.validator("time", precision).map(_.unwrap),
          FieldValidator.byName("hostname") { _.asString() },
          FieldValidator.byName("pointReq") { _.asLong() },
          
          FieldValidator.byName("pointReqLocal") { _.asLong() },
          FieldValidator.byName("req") { _.asLong() },
          FieldValidator.byName("subWriteDrop") { _.asLong() },
          FieldValidator.byName("subWriteOk") { _.asLong() },
          FieldValidator.byName("writeDrop") { _.asLong() },
          FieldValidator.byName("writeError") { _.asLong() },
          FieldValidator.byName("writeOk") { _.asLong() },
          FieldValidator.byName("writeTimeout") { _.asLong() })
          .mapN(WriteInternal(_,_,_,_,_,_,_,_,_,_,_))
          .run(data)

    }
}