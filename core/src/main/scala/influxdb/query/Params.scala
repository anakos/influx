package influxdb.query

import cats._
import cats.syntax.option._
import influxdb.{Natural, Precision}

final case class Params(query: List[String], dbName: Option[String], precision: Option[Precision]) {
  def toMap(): Map[String, String] =
    Map("q" -> query.mkString(";")) ++
      precision.map { p => "epoch" -> p.toString }.toMap ++
      dbName.map { d => "db" -> d }.toMap 
}
object Params {
  implicit val show: Show[Params] =
    Show.show(_.toMap().toList.mkString(" & "))
    
  def singleQuery(query: String): Params =
    Params(List(query), none, none)
  def singleQuery(query: String, dbName: String): Params =
    Params(List(query), dbName.some, none)
  def singleQuery(query: String, dbName: String, precision: Precision): Params =
    Params(List(query), dbName.some, precision.some)
  def singleQuery(query: String, precision: Precision): Params =
    Params(List(query), none, precision.some)

  def multiQuery(queries: List[String]): Params =
    Params(queries, none, none)
  def multiQuery(queries: List[String], dbName: String): Params =
    Params(queries, dbName.some, none)
  def multiQuery(queries: List[String], precision: Precision): Params =
    Params(queries, none, precision.some)
  def multiQuery(queries: List[String], dbName: String, precision: Precision): Params =
    Params(queries, dbName.some, precision.some)
}

final case class ChunkSize(unwrap: Option[Natural]) {
  def params(): Map[String, String] =
    unwrap.fold(Map(ChunkSize.param)) { size => Map(ChunkSize.param, "chunk_size" -> size.toString())}    
}
object ChunkSize {
  val param = "chunked" -> true.toString()

  def default() = ChunkSize(none)
  def withSize(value: Natural) =
    ChunkSize(value.some)
}