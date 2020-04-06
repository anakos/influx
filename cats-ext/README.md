influxdb-cats-ext
===============

This module extends the functionality provided in influxdb-core.  It lifts interaction with InfluxDb into the RIO (`ReaderT over IO`) monad, constraining the environment variable accordingly via interplay with the Has typeclass (https://github.com/anakos/data-has).

Integration tests for the library are also defined here.

# Module Structure

- influxdb.http            : get / post requests to InfluxDb
- influxdb.manage          : InfluxDb admin / management helpers
- influxdb.manage.db       : db management
- influxdb.manage.ping     : is the instance running?
- influxdb.manage.retention: retention policy management
- influxdb.manage.users    : user management
- influxdb.query           : query helpers
- influxdb.write           : write helpers (http)
- influxdb.write.udp       : write helpers (udp)

Common type aliases are defined in the package root