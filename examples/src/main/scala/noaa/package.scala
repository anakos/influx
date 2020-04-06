package object noaa {
  type App[A]         = influxdb.RIO[Env, A]
  
  val DEFAULT_DB_NAME = "NOAA_water_database" 
}