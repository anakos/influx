influxdb
=====================

This project is a fork of [scala-influxdb-client](https://github.com/paulgoldbaum/scala-influxdb-client) with the following modifications / additions:

- drops spray-json dependency in favour of circe.
- replaces scala Futures with cats.effect.IO as base context for all operations. 
- provides helper methods for adding InfluxDb to programs structured using [`ReaderT over IO`](https://www.fpcomplete.com/blog/2017/06/readert-design-pattern).

## Project Structure

This project is split between two modules:

- core    : defines the core data structures for interacting with InfluxDb
- cats-ext: extends the core with helper methods that interact with InfluxDb using `ReaderT over IO`. 

This split structure should be considered a work in progress: the 2 modules may be merged into one.  

## Installation

Add the following to your `build.sbt`

```scala
libraryDependencies += "io.github.anakos" %% "data-has" % "0.1.0"
libraryDependencies += "io.github.anakos" %% "influxdb-core" % "0.1.0"
# if optionally using cats-ext
libraryDependencies += "io.github.anakos" %% "influxdb-cats-ext" % "0.1.0"
```
