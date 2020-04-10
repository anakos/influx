package influxdb.query

package object types {
  type Value = Either3[BigDecimal, String, Boolean]
}
