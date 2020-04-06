package influxdb.http

package object api {
  type Value = Either3[BigDecimal, String, Boolean]
}