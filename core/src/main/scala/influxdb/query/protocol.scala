package influxdb
package query

import cats.instances.all._
import cats.syntax.either._
import cats.syntax.traverse._
import influxdb.query.types._
import io.circe._
import scala.collection.immutable.ListMap

object json {
  val parseResultsObject: Json => Decoder.Result[Vector[Json]] =
    _.hcursor.get[Vector[Json]]("results")
  val parseSeriesObjectLenient: Json => Decoder.Result[Vector[Json]] =
    _.hcursor.getOrElse[Vector[Json]]("series")(Vector.empty)

  def parseSeriesBody[A : QueryResults](json: Json): Decoder.Result[Vector[A]] = {
    val csr = json.hcursor
    csr.as[SeriesBody]
      .flatMap { case SeriesBody(name, TagSet(tags), columns, values) =>
        values
          .traverse { QueryResults[A].parseWithRaw(name, tags, columns, _) }
          .leftMap { io.circe.DecodingFailure(_, csr.history) }
      }
  }

  /**
    * parse results JSON. uses lenient parsing to skip non-series data (ie - errors, statements, anything else).
    *
    * @param content
    * @return
    */
  def parseResults[A : influxdb.query.QueryResults](json: Json): Decoder.Result[Vector[A]] =
    for {
      results <- parseResultsObject(json)
      series  <- results.traverse(parseSeriesObjectLenient)
      values  <- series.flatten.traverse(parseSeriesBody[A])
    } yield values.flatten

  /**
    * Series data decoder.
    * 
    * If non-series data is encountered (i.e. - statements, errors), it is simply ignored
    *
    * @param f
    * @return
    */
  def resultsDecoder[A : QueryResults]: Decoder[Vector[A]] =
    Decoder.instance[Vector[A]] { csr => parseResults(csr.value) }

  def decoder[A : QueryResults]: Decoder[Either[Failure, Vector[A]]] =
    Decoder[Failure]
      .either(resultsDecoder[A])
  
  def parseQueryResult[A : QueryResults](js: Json): Either[InfluxException, Vector[A]] =
    decoder[A]
      .apply(js.hcursor)
      .leftMap { InfluxException.unexpectedResponse(js, _) }
      .flatMap { _.leftMap { _.toInfluxException() } }
}

/**
  * Raw series JSON representation
  */
final case class SeriesBody(name: Option[String], tags: TagSet, columns: Vector[String], values: Vector[Vector[Nullable]])
object SeriesBody {
  implicit val decoder: Decoder[SeriesBody] =
    Decoder.instance[SeriesBody] { csr =>
      for {
        name    <- csr.get[Option[String]]("name")
        columns <- csr.get[Vector[String]]("columns")
        tags    <- csr.getOrElse[TagSet]("tags")(TagSet.empty())
        values  <- csr.getOrElse[Vector[Vector[Nullable]]]("values")(Vector.empty)
      } yield SeriesBody(name, tags, columns, values)      
    }
}

final case class TagSet(unwrap: ListMap[String, Value])
object TagSet {
  implicit val decoder: Decoder[TagSet] =
    Decoder[JsonObject]
      .emap(
        _.toList
         .traverse { case (key, value) =>
           value.as[Value]
             .bimap(
               _ => s"failed to decode tag with key [$key] as a nullable primitive [${value.noSpaces}]",
               (key, _)
             )
          }
          .map(x => TagSet(ListMap.from(x)))
      )

  def empty() = TagSet(ListMap.empty)
}

/**
 * represents an error message direct from the server
 */
final case class Failure(error: String) {
  def toInfluxException(): InfluxException =
    InfluxException.ServerError(error)
}
object Failure {
  implicit val decoder: Decoder[Failure] =
    io.circe.generic.semiauto.deriveDecoder[Failure]
}
