influxdb
=====================

A scala based influxdb client.

This project started out as a fork of [scala-influxdb-client](https://github.com/paulgoldbaum/scala-influxdb-client), but diverged enough to merit a separate project entirely:

- drops spray-json dependency in favour of circe.
- drops as-hoc asynchttpclient client wrapper in favour of sttp.
- supports chunked queries   
- drops scala Futures as the base effect-type for all operations, providing separate extension modules for both ZIO and cats-effect 

The existing test suite from [scala-influxdb-client](https://github.com/paulgoldbaum/scala-influxdb-client) has been replicated here as much as possible to validate that all functionality has been preserved. 

# Motivation

Working with InfluxDB on a recent project, I found myself want for a library that embraced functional constructs and integrated (out of the box) with my existing project structure.  

That library didn't exist so here we go.

# Project Structure

This project consists of the following modules:

- core    : defines the core data structures for interacting with InfluxDb
- cats-ext: extends the core with helper methods that interact with InfluxDb, and are structured using [`ReaderT over IO`](https://www.fpcomplete.com/blog/2017/06/readert-design-pattern). This code depends on both cats-effect and fs2. 
- zio-ext:  extends the core with helper methods that interact with InfluxDb defined in terms of ZIO andZIO-Streams.

See individual READMEs for a deeper dive on structure. As an aside, I've tried to design this for qualified import (see: https://jaspervdj.be/posts/2018-03-08-handle-pattern.html) and will be revisiting the overall structure to ensure that this goal has been met.

# Installation

Docker is required to run the intergration tests and the example projects.

## cats-ext

Add the following to your `build.sbt`

```scala
libraryDependencies += "io.github.anakos" %% "data-has" % "0.1.0"
libraryDependencies += "io.github.anakos" %% "influxdb-core" % "0.1.0"
libraryDependencies += "io.github.anakos" %% "influxdb-cats-ext" % "0.1.0"
```

## zio-ext

Add the following to your `build.sbt`

```scala
libraryDependencies += "io.github.anakos" %% "influxdb-core" % "0.1.0"
libraryDependencies += "io.github.anakos" %% "influxdb-zio-ext" % "0.1.0"
```

# Examples

Example projects are provided for both the cats & zio variants. Both projects provide a working application that publishes data to a local instance of InfluxDb and then queries it.  The code is based on the sample data / queries defined here:

https://docs.influxdata.com/influxdb/v1.8/query_language/data_download/

See the accompanying READMEs for more information.

## TODO

1. add attribution where applicable.
2. cleanup. ZIO integration has been added, it's a good idea to do some tidying up and ensure things are as consistent as they can be across projects 