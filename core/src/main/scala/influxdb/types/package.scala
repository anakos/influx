package influxdb

package object types {
  def escapeString(str: String) =
    str.replaceAll("([ ,=])", "\\\\$1")  
}
