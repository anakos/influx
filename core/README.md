influx-core
==========

This module should provide all the necessary functionality for interacting with InfluxDB, with a minimal set of dependencies.  

# Module Structure

- influxdb.http      : http client for interacting with an InfluxDB instance
- influxdb.query     : classes for generating query requests and parsing query responses 
- influxdb.query.json: logic for decoding InfluxDB JSON responses.
- influxdb.write     : classes for generating write requests / serializing write data.
- influxdb.write.udp : udp client for writing data to an InfluxDB instance

# Dependencies

cats / cats-effect / fs2
circe
sttp

## JSON deserialization

We provide a lenient decoding of JSON responses from InfluxDB: top level errors will yield an explicit failure, other errors present in the result set are merely skipped.  
To some extent, this mirrors the behaviour of the Haskell InfluxDB client, but we can amend and add strict decoding behaviour in the future.

The QueryResults trait defines the behaviour for transforming series data into structured data (i.e. - in the form of a case class).
