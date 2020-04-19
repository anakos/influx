package object noaa {
  import zio._

  type Env     = ZEnv with Logging with influxdb.InfluxDB
  type App[A]  = ZIO[Env, influxdb.InfluxException, A]
  type Logging = Has[Logging.Service]
  
  val DEFAULT_DB_NAME = "NOAA_water_database" 
}