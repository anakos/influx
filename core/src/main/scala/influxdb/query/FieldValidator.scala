package influxdb.query

import cats.data.ReaderT
import cats.syntax.either._
import influxdb.query.types.Nullable
import scala.collection.immutable.ListMap

object FieldValidator {
  type Validator[A] = ReaderT[Either[String, ?], ListMap[String, Nullable], A]

  def byName[A](fieldName: String)(f: Nullable => Option[A]): Validator[A] =
    ReaderT { fields =>
      fields
        .get(fieldName)
        .toRight(s"no field named $fieldName present with required type in [ ${fields.toList.mkString(" , ")} ]")
        .flatMap { value =>
          f(value).toRight(s"error converting value [$value] for $fieldName to required type")
        }
    }

  def byIndex[A](idx: Int)(f: Nullable => Option[A]): Validator[A] =
    ReaderT { fields =>
      Either.catchNonFatal(fields.toVector(idx))
        .bimap(
          _ => s"no element at index $idx in [ ${fields.toList.mkString(" , ")} ]",
          _._2
        )
        .flatMap(
          f.andThen(
            _.toRight(s"field at $idx was not present with required type in [ ${fields.toList.mkString(" , ")} ]")
          )
        )
    }
}