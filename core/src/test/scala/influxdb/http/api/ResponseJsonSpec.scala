package influxdb
package http
package api

import cats.syntax.option._
import org.specs2.mutable
import influxdb.types.Either3
import io.circe._
import io.circe.generic.auto._

class ResponseJsonSpec extends mutable.Specification {
  type SeriesResult = QueryResult.Default
  "Construct result" >> {
    "with series data" >> {
      val data = """{"results":[{"series":[{"name":"databases","columns":["name"],"values":[["_internal"]],"tags":{"tag": "value"}}]}]}"""
      jawn.decode[SeriesResult](data) must beRight[SeriesResult].like {
        case QueryResult(Right(results)) => results must have size 1
      }
    }

    "with statement data" >> {
      val data =     """{"results":[{"statement_id":0}]}"""
      jawn.decode[SeriesResult](data) must beRight[SeriesResult].like {
        case QueryResult(Right(List(result))) =>
          result.unwrap must_=== Either3.Middle3(Statement(0.some))
      }
    }
  }

  "Construct record" >> {
    "from valid JSON" >> {
      val Right(data) = jawn.decode[Vector[Json]]("""[1, "second value"]""")
      Record.create(Vector("first_metric", "second_metric"), data) must beRight[Record].like {
        case record =>
          record(0) must_=== PrimitivePlus.fromNum(1)
          record("first_metric") must_=== PrimitivePlus.fromNum(1)
          record(1) must_=== PrimitivePlus.fromString("second value")
          record("second_metric") must_=== PrimitivePlus.fromString("second value")
      }
    }

    "null values are supported" >> {
      val Right(data) = jawn.decode[Vector[Json]]("""[1, null]""")

      Record.create(Vector("first_metric", "second_metric"), data) must beRight[Record].like {
        case record =>
          record(1) must_=== PrimitivePlus.fromNull()
          record("second_metric") must_=== PrimitivePlus.fromNull()
      }
    }

    "unsupported types throws a MalformedResponseException" >> {
      val Right(data) = jawn.decode[Vector[Json]]( """[{}, "second value"]""")
      Record.create(Vector("first_metric", "second_metric"), data) must beLeft[String]
    }
  }

  "Construct SingleSeries from JSON" >> {
    "with name" >> {
      jawn.decode[SingleSeries](
        """{"name":"test_series","columns":["column1", "column2", "column3"],"values":[["value1", 2, true]],"tags":{"tag": "value"}}"""
      ) must beRight[SingleSeries].like {
        case SingleSeries(name, columns, records, tags) =>
          name must_=== "test_series"
          columns must_=== Vector("column1", "column2", "column3")
          tags.size must_=== 1
          records.length must_=== 1
          records.headOption must beSome[Record].like {
            case record =>
              record("column1") must_=== PrimitivePlus.fromString("value1")
              record("column2") must_=== PrimitivePlus.fromNum(2)
              record("column3") must_=== PrimitivePlus.fromBool(true)
              record.allValues.length must_=== 3
          }
      }
    }

    "without name" >> {
      jawn.decode[SingleSeries](
        """{"columns":["column1", "column2", "column3"],"values":[["value1", 2, true]],"tags":{"tag": "value"}}"""
      ) must beRight[SingleSeries].like {
        case SingleSeries(name, columns, records, tags) =>
          name must_=== ""
          columns must_=== Vector("column1", "column2", "column3")
          records.length must_=== 1
          tags.size must_=== 1
      }
    }

    "without values" >> {
      jawn.decode[SingleSeries](
        """{"columns":["column1", "column2", "column3"]}"""
      ) must beRight[SingleSeries].like {
        case SingleSeries(name, columns, records, _) =>
          name must_=== ""
          columns must_=== Vector("column1", "column2", "column3")
          records must beEmpty
      }
    }

    //  throws a MalformedResponseException
    "fails with unsupported types" >> {
      jawn.decode[SingleSeries](
        """{"name":"test_series","columns":[1],"values":[]}"""
      ) must beLeft
    }

    //  throws a MalformedResponseException
    "fails with unsupported tag types" >> {
      jawn.decode[SingleSeries](
        """{"name":"test_series","columns":["columns1"],"values":[["value1"]],"tags": {"tag": []}}"""
      ) must beLeft
    }
  }

  "Value series can be accessed by name, position and as a list" >> {
    jawn.decode[SingleSeries](
      """{"name":"n","columns":["column1", "column2"],"values":[[1, 2],[2, 3],[3, 4],[4, 5]]}"""
    ) must beRight[SingleSeries].like {   
      case series =>
        series.points("column1") must_=== Vector(1, 2, 3, 4).map { PrimitivePlus.fromNum(_) }
        series.points(0) must_=== Vector(1, 2, 3, 4).map { PrimitivePlus.fromNum(_) }
        series.points("column2") must_=== Vector(2, 3, 4, 5).map { PrimitivePlus.fromNum(_) }
        series.points(1) must_=== Vector(2, 3, 4, 5).map { PrimitivePlus.fromNum(_) }
        series.allValues must_=== Vector(
          Vector(1, 2).map { PrimitivePlus.fromNum(_) },
          Vector(2, 3).map { PrimitivePlus.fromNum(_) },
          Vector(3, 4).map { PrimitivePlus.fromNum(_) },
          Vector(4, 5).map { PrimitivePlus.fromNum(_) }
        )
    }
  }

  "Tags" >> {
    "can be accessed by name and position" >> {
      jawn.decode[SingleSeries](
        """{"columns":["column1", "column2", "column3"],"values":[["value1", 2, true]],"tags":{"tag": "value"}}"""
      ) must beRight[SingleSeries].like {
        case series =>
          series.tags("tag") must_=== JsPrimitive.string("value")
          series.tags(0) must_=== JsPrimitive.string("value")
      }
    }

    "can be defined as strings, numbers or booleans" >> {
      jawn.decode[SingleSeries](
        """{"columns":["column1", "column2", "column3"],"values":[["value1", 2, true]],"tags":{"tag1": "value", "tag2": 10, "tag3": true}}"""
      ) must beRight[SingleSeries].like {
        case series =>
          series.tags("tag1") must_=== JsPrimitive.string("value")
          series.tags(0) must_=== JsPrimitive.string("value")
          series.tags("tag2") must_=== JsPrimitive.num(10)
          series.tags(1) must_=== JsPrimitive.num(10)
          series.tags("tag3") must_=== JsPrimitive.bool(true)
          series.tags(2) must_=== JsPrimitive.bool(true)
      }
    }
  }

  "Valid error responses throws an ErrorResponseException" >> {
    jawn.decode[SeriesResult](
      """{"results":[{"error":"database not found: _test"}]}"""
    ) must beRight[SeriesResult].like {
      case QueryResult(Right(results)) =>
        results must haveSize(1)
        results.headOption must beSome[Result.Default].like {
          case Result(Either3.Left3(failure)) =>
            failure.error must_=== "database not found: _test"
        }
    }
  }

  "Empty responses return an empty statement (no series)" >> {
    jawn.decode[SeriesResult](
      """{"results":[{}]}"""
    ) must beRight.like {
      case QueryResult(Right(List(Result(Either3.Middle3(x))))) =>
        x.id must beEmpty
    }
  }

  "Multiple results are parsed correctly" >> {
    jawn.decode[SeriesResult](
      """{"results":[{"series":[{"name":"databases","columns":["name"],"values":[["_internal"]],"tags":{"tag": "value"}}]},{"series":[{"name":"databases_2","columns":["name"],"values":[["_internal"]],"tags":{"tag": "value"}}]}]}"""
    ) must beRight[SeriesResult].like {
      case QueryResult(Right(List(result1, result2))) =>
        result1.getSeries() must beSome[Series.Default].like {
          case Series(List(series1)) =>
            series1.name must_=== "databases"
        } 
        result2.getSeries() must beSome[Series.Default].like {
          case Series(List(series2)) =>
            series2.name must_=== "databases_2"
        }        
    }
  }
}
