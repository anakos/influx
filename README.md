influxdb
=====================

This project is a fork of [scala-influxdb-client](https://github.com/paulgoldbaum/scala-influxdb-client) with the following modifications / additions:

- drops spray-json dependency in favour of circe.
- replaces scala Futures with cats.effect.IO as base context for all operations. 
- provides helper methods for adding InfluxDb to programs structured using [`ReaderT over IO`](https://www.fpcomplete.com/blog/2017/06/readert-design-pattern).

The existing test suite has been replicated here as much as possible to validate that all functionality has been preserved. 

# Motivation

Working with InfluxDB on a recent project, I found myself want for a library that embraced functional constructs and integrated (out of the box) with my existing project structure.  

That library didn't exist so here we go.

# Project Structure

This project is split between two modules:

- core    : defines the core data structures for interacting with InfluxDb
- cats-ext: extends the core with helper methods that interact with InfluxDb using `ReaderT over IO`. 

This split structure should be considered a work in progress: the 2 modules may be merged into one.  

See individual READMEs for a deeper dive on structure. As an aside, I've tried to design this for qualified import (see: https://jaspervdj.be/posts/2018-03-08-handle-pattern.html) and will be revisiting the overall structure to ensure that this goal has been met.

## Installation

Add the following to your `build.sbt`

```scala
libraryDependencies += "io.github.anakos" %% "data-has" % "0.1.0"
libraryDependencies += "io.github.anakos" %% "influxdb-core" % "0.1.0"
# if optionally using cats-ext
libraryDependencies += "io.github.anakos" %% "influxdb-cats-ext" % "0.1.0"
```

# Examples

The examples module provides a working application that publishes data to a local instance of InfluxDb and then queries it.  The code is based on the sample data / queries defined here:

https://docs.influxdata.com/influxdb/v1.7/query_language/data_download/

See the accompanying README of that project for more information.

# Future Development / TODO

## TODO

1. add attribution where applicable.
2. support chunked queries directly.

## Future Development

This is very much a work in progress. I'd like to add / investigate the following:

- ZIO integration. 
- Plugable HTTP client backend / STTP integration
