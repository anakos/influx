package influxdb
package query

import cats.instances.all._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.option._
import influxdb.query.{json => JSON}
import influxdb.query.types._
import io.circe._
import org.specs2.mutable
import scala.collection.immutable.ListMap

class JsonSpec extends mutable.Specification {
  "lenient results parsing" >> {
    implicit val lenientDecoder: Decoder[Vector[SeriesResult]] =
      JSON.resultsDecoder[SeriesResult](DecodingStrategy.lenient[SeriesResult](None))

    "returns valid series data as a single collection" >> {
      jawn.decode[Vector[SeriesResult]](
        """{"results":[{"series":[{"name":"databases","columns":["name"],"values":[["_internal"]],"tags":{"tag": "value"}}]}]}"""
      ) must beRight[Vector[SeriesResult]].like {
        case results => results must have size 1
      }
    }

    "ignores nested error messages" >> {
      jawn.decode[Vector[SeriesResult]](
        """{"results":[{"error":"database not found: _test"}]}"""
      ) must beRight[Vector[SeriesResult]].like {
        case results => results must beEmpty
      }
    }

    "ignores statement data" >> {
      jawn.decode[Vector[SeriesResult]]("""{"results":[{"statement_id":0}]}""") must beRight[Vector[SeriesResult]].like {
        case results => results must beEmpty
      }
    }

    "flattens multiple series into a single collection" >> {
      jawn.decode[Vector[SeriesResult]](
        """{"results":[{"series":[{"name":"databases","columns":["name"],"values":[["_internal_1"]],"tags":{"tag": "value"}}]},{"series":[{"name":"databases_2","columns":["name"],"values":[["_internal_2"]],"tags":{"tag": "value"}}]}]}"""
      ) must beRight[Vector[SeriesResult]].like {
        case Vector(result1, result2) =>
          result1.name must_=== "_internal_1"
          result2.name must_=== "_internal_2"
      }
    }

    "empty results yields an empty vector" >> {
      jawn.decode[Vector[SeriesResult]]("""{"results":[{}]}""") must beRight.like {
        case results => results must beEmpty
      }
    }    
  }

  "strict results parsing" >> {
    implicit val strictDecoder: Decoder[Vector[SeriesResult]] =
      JSON.resultsDecoder[SeriesResult](DecodingStrategy.strict[SeriesResult](None))

    "stops at nested error messages" >> {
      jawn.decode[Vector[SeriesResult]](
        """{"results":[{"error":"database not found: _test"}]}"""
      ) must beRight[Vector[SeriesResult]].like {
        case results => results must beEmpty
      }
    }
  }

  "SeriesBody" >> {
    "from minimum valid JSON" >> {
      jawn.decode[SeriesBody]("""{ "columns": [] }""") must beRight.like {
        case SeriesBody(name, tags, columns, values) =>
          name must beEmpty
          tags.unwrap must beEmpty
          columns must beEmpty
          values must beEmpty
      }
    }

    "from valid JSON with name" >> {
      jawn.decode[SeriesBody](
        """{"name":"test_series","columns":["column1", "column2", "column3"],"values":[["value1", 2, true]],"tags":{"tag": "value"}}"""
      ) must beRight[SeriesBody].like {
        case SeriesBody(name, tags, columns, values) =>
          name must_=== "test_series".some
          columns must_=== Vector("column1", "column2", "column3")
          tags.unwrap.size must_=== 1
          values must beLike {
            case Vector(vals) =>
              vals(0) must_=== Nullable.fromString("value1")
              vals(1) must_=== Nullable.fromNum(2)
              vals(2) must_=== Nullable.fromBool(true)
          }
      }
    }

    "null values are supported" >> {
      jawn.decode[SeriesBody](
        """{"name":"test_series","columns":["column1", "column2"],"values":[[1, null], [null, "hello"]],"tags":{"tag": "value"}}"""
      ) must beRight[SeriesBody].like {
        case SeriesBody(_,_,_,values) =>
          values must beLike {
            case Vector(vals1, vals2) =>
              vals1(1).asNull() must beSome
              vals2(0).asNull() must beSome
          }
      }
    }

    "tags are optional" >> {
      jawn.decode[SeriesBody](
        """{"name":"test_series","columns":["column1", "column2", "column3"],"values":[["value1", 2, true]] }"""
      ) must beRight[SeriesBody].like {
        case SeriesBody(_, tags, _, _) =>
          tags.unwrap must beEmpty
      }
    }

    "fails when unsupported types are provided" >> {
      jawn.decode[SeriesBody](
        """{"name":"test_series","columns":["valid", "invalid"],"values":[[2, {} ]] }"""
      ) must beLeft
    }
  }
}

final case class SeriesResult(name: String, tag: String)
object SeriesResult {
  implicit val queryResults: QueryResults[SeriesResult] = 
    new QueryResults[SeriesResult] {
      def parseWith(_precision: Option[Precision], name: Option[String], tags: ListMap[String, Value], fields: ListMap[String, Nullable]) =
        (FieldValidator.byName("name") { _.asString() }.run(fields),
            tags.get("tag")
              .toRight("no tag in result")
              .flatMap(_.middleOr("tag was not a string!".asLeft[String])(_.asRight[String]))
        ).mapN { SeriesResult(_, _) }
    }
}