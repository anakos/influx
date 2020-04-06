package influxdb.write

object utils {
  def escapeString(str: String) =
    str.replaceAll("([ ,=])", "\\\\$1")
}