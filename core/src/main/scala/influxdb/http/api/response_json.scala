package influxdb
package http
package api

import cats.instances.all._
import cats.syntax.either._
import cats.syntax.monadError._
import cats.syntax.option._
import cats.syntax.show._
import cats.syntax.traverse._
import influxdb.types.Either3
import io.circe._

final case class Failure(error: String) {
  def toInfluxException(): InfluxException =
    InfluxException.ServerError(error)
}

final case class Record(namesIndex: Map[String, Int], values: Vector[PrimitivePlus]) {
  def apply(position: Int) = values(position)
  def apply(name: String) = values(namesIndex(name))
  def allValues = values
}
object Record {
  def create(names: Vector[String], values: Vector[Json]): Either[String, Record] =
    values
      .asRight[String]
      .ensure(s"could not create Record from mismatched number of columns (${names.length}) and values (${values.length})")(_.length == names.length)
      .flatMap(_.traverse(_.as[PrimitivePlus]).leftMap(_.show))
      .map { values => Record(names.zipWithIndex.toMap, values) }
}

final case class Tags(tagsIndex: Map[String, Int], values: Vector[JsPrimitive]) {
  def apply(position: Int) = values(position)
  def apply(name: String) = values(tagsIndex(name))
  def size: Int = tagsIndex.size
}
object Tags {
  implicit val decoder: Decoder[Tags] =
    Decoder[JsonObject].emap { js =>
      val tagsIndex = js.keys.zipWithIndex.toMap
      js.values
        .toVector
        .traverse[Either[DecodingFailure, ?], JsPrimitive] { _.as[JsPrimitive] }
        .bimap(
          _.show,
          values => Tags(tagsIndex, values)
        )
    }

  def empty(): Tags =
    Tags(Map.empty, Vector.empty)
}

// TODO: is it necessary to preserve columns in this data type?
final case class SingleSeries(name: String, columns: Vector[String], records: Vector[Record], tags: Tags) {
  def points(column: String) = records.map(_(column))
  def points(column: Int) = records.map(_(column))
  def allValues = records.map(_.allValues)
}
object SingleSeries {
  import io.circe.generic.auto._
  private[SingleSeries] final case class Js(name: Option[String], columns: Option[Vector[String]], values: Option[Vector[Vector[Json]]], tags: Option[Tags])

  implicit val decoder: Decoder[SingleSeries] =
    Decoder[Js].emap { js =>
      val cols = js.columns.getOrElse(Vector.empty)
      js.values
        .toVector
        .flatten
        .traverse { Record.create(cols, _) }
        .map { records =>
          SingleSeries(js.name.getOrElse(""), cols, records, js.tags.getOrElse(Tags.empty()))
        }
    }
}

final case class Series[A](unwrap: List[A])
object Series {
  type Default = Series[SingleSeries]

  implicit def decoder[A : Decoder]: Decoder[Series[A]] =
    Decoder.instance { _.downField("series").as[List[A]] }
      .map { Series(_) }  
}

final case class Result[A](unwrap: Either3[Failure, Statement, Series[A]]) {
  def getFailure(): Option[Failure] =
    unwrap.fold(_.some, _ => none, _ => none)
  def getSeries(): Option[Series[A]] =
    unwrap.fold(_ => none, _ => none, _.some)
  def failOnError(): Either[InfluxException, List[A]] =
    unwrap.fold(
      _.toInfluxException().asLeft,
      _ => Nil.asRight,
      _.unwrap.asRight,
    )
}
object Result {
  type Default = Result[SingleSeries]

  implicit def decoder[A : Decoder]: Decoder[Result[A]] = {
    import io.circe.generic.auto._

    Decoder[Series[A]].map { x => Result(Either3.right3[Failure, Statement, Series[A]](x)) }
      .or(Decoder[Failure].map { x => Result(Either3.left3[Failure, Statement, Series[A]](x)) })
      .or(Decoder[Statement].map { x => Result(Either3.middle3[Failure, Statement, Series[A]](x))})
  }
}

final case class QueryResult[A](unwrap: Either[Failure, List[Result[A]]])
object QueryResult {
  type Default = QueryResult[SingleSeries]
  
  implicit def decoder[A : Decoder]: Decoder[QueryResult[A]] = {
    import io.circe.generic.auto._

    Decoder[Failure]
      .either(Decoder.instance { _.downField("results").as[List[Result[A]]] })
      .map { QueryResult(_) }
  }

  def empty[A](): QueryResult[A] =
    QueryResult(Nil.asRight)
}

final case class Statement(id: Option[Int])
object Statement {
  def empty() =
    Statement(none)
  implicit val decoder: Decoder[Statement] =
    Decoder.instance[Statement] {_.downField("statement_id").as[Int].map(x => Statement(x.some)) }
      .or(
        Decoder[JsonObject].ensure(_.isEmpty, "failed to decode empty JSON object")
          .map { _ => Statement.empty() }
      )
}