influx-core
==========

This module should provide all the necessary functionality for interacting with InfluxDB, with a minimal set of dependencies.

# Module Structure

- influxdb.http     : http client for interacting with an InfluxDB instance
- influxdb.http.api : classes for decoding InfluxDB JSON responses.
- influxdb.query    : classes for generating query requests and parsing query responses 
- influxdb.write    : classes for generating write requests / serializing write data.
- influxdb.write.udp: udp client for writing data to an InfluxDB instance

# Dependencies

cats / cats-effect
circe
async-http-client

