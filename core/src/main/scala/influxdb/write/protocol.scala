package influxdb.write

import cats.syntax.either._

final case class Point(key: String, timestamp: Long, tags: Seq[Tag], fields: Seq[Field]) {
  def addTag(key: String, value: String) = copy(tags = Tag(key, value) +: tags)
  def addField(key: String, value: String) = copy(fields = Field.String(key, value) +: fields)
  def addField(key: String, value: Double) = copy(fields = Field.Double(key, value) +: fields)
  def addField(key: String, value: Long) = copy(fields = Field.Long(key, value) +: fields)
  def addField(key: String, value: Boolean) = copy(fields = Field.Boolean(key, value) +: fields)
  def addField(key: String, value: BigDecimal) = copy(fields = Field.BigDecimal(key, value) +: fields)

  def serialize() = {
    val sb = new StringBuilder
    sb.append(escapeKey(key))
    if (tags.nonEmpty) {
      sb.append(",")
      sb.append(tags.map(_.serialize).mkString(","))
    }

    if (fields.nonEmpty) {
      sb.append(" ")
      sb.append(fields.map(_.serialize).mkString(","))
    }

    if (timestamp > 0) {
      sb.append(" ")
      sb.append(timestamp)
    }

    sb.toString()
  }

  private def escapeKey(key: String) = key.replaceAll("([ ,])", "\\\\$1")
}
object Point {
  def withDefaults(key: String): Point =
    withDefaults(key,  -1L)

  def withDefaults(key: String, timestamp: Long): Point =
    Point(
      key,
      timestamp,
      tags      = Nil,
      fields    = Nil
    )
}

// TODO: come back to this. this should be Field[A : Escapable] where Escapable is defined for the primitives listed below
sealed trait Field extends Product with Serializable {
  def serialize(): String
}
object Field {
  final case class String(key: scala.Predef.String, value: scala.Predef.String) extends Field {
    override def serialize() = utils.escapeString(key) + "=\"" + value.replaceAll("\"", "\\\\\"") + "\""
  }

  final case class Double(key: scala.Predef.String, value: scala.Double) extends Field {
    override def serialize() = s"${utils.escapeString(key)}=$value"
  }

  final case class Long(key: scala.Predef.String, value: scala.Long) extends Field {
    override def serialize() = s"${utils.escapeString(key)}=${value}i"
  }

  final case class Boolean(key: scala.Predef.String, value: scala.Boolean) extends Field {
    override def serialize() = s"${utils.escapeString(key)}=$value"
  }

  final case class BigDecimal(key: scala.Predef.String, value: scala.BigDecimal) extends Field {
    override def serialize() = s"${utils.escapeString(key)}=$value"
  }
}

final case class Tag(key: String, value: String) {
  def serialize(): String =
    s"${utils.escapeString(key)}=${utils.escapeString(value)}"
}
object Tag {
  def mk(key: String, value: String): Either[String, Tag] =
    Option(value).toRight("Tag values may not be null")
      .ensure("Tag values may not be empty")(_.nonEmpty)
      .map { Tag(key, _) }
}

object Parameter {
  sealed abstract class Precision(str: String) extends Serializable {
    override def toString = str
  }
  object Precision {
    case object NANOSECONDS extends Precision("ns")
    case object MICROSECONDS extends Precision("u")
    case object MILLISECONDS extends Precision("ms")
    case object SECONDS extends Precision("s")
    case object MINUTES extends Precision("m")
    case object HOURS extends Precision("h")
  }

  sealed abstract class Consistency(str: String) extends Serializable {
    override def toString = str
  }
  object Consistency {
    case object ONE extends Consistency("one")
    case object QUORUM extends Consistency("quorum")
    case object ALL extends Consistency("all")
    case object ANY extends Consistency("any")
  }
}