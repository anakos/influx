package influxdb
package query

import cats.instances.all._
import cats.syntax.either._
import cats.syntax.foldable._
import cats.syntax.traverse._
import influxdb.query.types._
import io.circe._
import scala.collection.immutable.ListMap

object json {
  val parseResultsObject: Json => Decoder.Result[Vector[Json]] =
    _.hcursor.get[Vector[Json]]("results")
  val parseSeriesObject: Json => Decoder.Result[Vector[Json]] =
    js => js.hcursor.get[Vector[Json]]("series")
      .orElse(parseResultsObject(js))
      .orElse(Vector.empty.asRight)
  val parseSeriesObjectLenient: Json => Decoder.Result[Vector[Json]] =
    _.hcursor.getOrElse[Vector[Json]]("series")(Vector.empty)
  def parseErrorAsDecodingFailure[A]: Json => Decoder.Result[A] =
    js => Decoder[Failure].flatMap { case Failure(msg) => Decoder.failedWithMessage[A](msg) }
      .apply(js.hcursor) 
  def parseSeriesBody[A : QueryResults](json: Json): Decoder.Result[Vector[A]] = {
    val csr = json.hcursor
    csr.as[SeriesBody]
      .flatMap { case SeriesBody(name, TagSet(tags), columns, values) =>
        values
          .traverse { values =>
            QueryResults[A]
              .parseWithRaw(name, tags, columns, values)
              .leftMap { io.circe.DecodingFailure(_, csr.history) }
          }
      }
  }

  /**
    * parse results JSON using the provided strategy.
    *
    * @param strategy 
    * @param json
    * @return
    */
  def parseResultsWithStrategy[A : influxdb.query.QueryResults](strategy: DecodingStrategy[A], json: Json): Decoder.Result[Vector[A]] =
    parseResultsObject(json).flatMap {
      _.foldMap { results =>
        for {
          series <- strategy.parseSeriesObject(results)
          result <- series.flatTraverse(strategy.parseSeries)
        } yield result
      }  
    }

  /**
    * parse results JSON. uses lenient parsing to skip non-series data (ie - errors, statements, anything else).
    *
    * @param content
    * @return
    */
  def parseResults[A : influxdb.query.QueryResults](json: Json): Decoder.Result[Vector[A]] =
    parseResultsWithStrategy[A](DecodingStrategy.lenient[A], json)

  def resultsDecoder[A : QueryResults](strategy: DecodingStrategy[A]): Decoder[Vector[A]] =
    Decoder.instance[Vector[A]] { csr => parseResultsWithStrategy(strategy, csr.value) }

  def decoder[A : QueryResults](strategy: DecodingStrategy[A]): Decoder[Either[Failure, Vector[A]]] =
    Decoder[Failure]
      .either(resultsDecoder[A](strategy))

  def parseQueryResultWithDecoder[A : QueryResults](strategy: DecodingStrategy[A], js: Json): Either[InfluxException, Vector[A]] =
    decoder[A](strategy)
      .apply(js.hcursor)
      .leftMap { InfluxException.unexpectedResponse(js, _) }
      .flatMap { _.leftMap { _.toInfluxException() } }

  /**
    * parse results JSON using lenient parsing strategy, transforming decoding failures into an InfluxException.
    * 
    * this is the default means of parsing query results
    *
    * @param js
    * @return
    */
  def parseQueryResult[A : QueryResults](js: Json): Either[InfluxException, Vector[A]] =
    parseQueryResultWithDecoder[A](DecodingStrategy.lenient[A], js)
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

final case class DecodingStrategy[A](
  parseSeriesObject: Json => Decoder.Result[Vector[Json]],
  parseSeries      : Json => Decoder.Result[Vector[A]]
)
object DecodingStrategy {
  /**
    * This is the default strategy for consuming Query result from InfluxDB.
    * Rather than failing when encountering non-series data, this will skip the unexpected result.
    * 
    * For example, the following response from InfluxDB will yield an empty collection:
    *
    * @return
    */
  def lenient[A : QueryResults]: DecodingStrategy[A] =
    DecodingStrategy[A](json.parseSeriesObjectLenient, json.parseSeriesBody[A](_))
  /**
   * This strategy enforces strict parsing rules when consuming Query results from InfluxDB.
   * Error messages are propagated as Decoding failures.
   */ 
  def strict[A : QueryResults]: DecodingStrategy[A] =
    DecodingStrategy[A](json.parseSeriesObject, json.parseSeriesBody[A](_))
}