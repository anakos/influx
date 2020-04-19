import Dependencies._

name := "influx"

ThisBuild / publishTo := sonatypePublishToBundle.value

inThisBuild(
  List(
    organization := "io.github.anakos",
    scalaVersion := "2.13.1",
    homepage := Some(url("https://github.com/anakos/influx")),
    scmInfo := Some(ScmInfo(url("https://github.com/anakos/influx"), "git@github.com:anakos/influx.git")),
    developers := List(Developer("anakos", "Alexander Nakos", "anakos@gmail.com", url("https://github.com/anakos"))),
    licenses += ("MIT", url("https://github.com/anakos/data-has/blob/master/LICENSE")),
    publishMavenStyle := true,
    addCompilerPlugin("org.typelevel" % "kind-projector_2.13.1" % "0.11.0"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
  )
)

lazy val root = (project in file("."))
  .settings(
    publish         := {},
    publishLocal    := {},
    publishArtifact := false,
  )
  .aggregate(core, `cats-ext`, `examples-cats`, `zio-ext`, `examples-zio`, `examples-app-data`)

lazy val core =
  mkProject("core")
    .settings(
      libraryDependencies ++= List(
        "org.tpolecat"           %% "atto-core"          % "0.7.2",
        circe.core,
        circe.generic,
        circe.jawn,
        sttp.core,
        cats.effect              % Test,
        cats.scalaCheck          % Test,
      )
    )
    
lazy val `cats-ext` =
  mkProject("cats-ext")
    .configs(IntegrationTest)
    .settings(
      Defaults.itSettings,
      libraryDependencies ++= List(
        has,
        cats.effect,
        circe.fs2,
        sttp.async_client,
        sttp.async_client_cats,
        sttp.async_client_fs2,
        "com.github.tomakehurst" % "wiremock" % "2.26.3" % Test,
        specs2.cats % IntegrationTest,
        specs2.core % IntegrationTest,
      )
    ).dependsOn(core)

lazy val `zio-ext` =
  mkProject("zio-ext")
    // .configs(IntegrationTest)
    .settings(
      // Defaults.itSettings,
      // testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      libraryDependencies ++= List(
        zio.core,
        zio.interop,
        zio.streams,
        sttp.async_client_zio,
        sttp.async_client_zio_streams,
        "com.github.tomakehurst" % "wiremock" % "2.26.3" % Test,
        // specs2.core % IntegrationTest,
      )
    ).dependsOn(core)

lazy val `examples-cats` =
  exampleProject("cats-app")
    .settings(
      libraryDependencies ++= List(
        fs2.core,
        fs2.io
      )
    )
    .dependsOn(core, `cats-ext`, `examples-app-data`)

lazy val `examples-zio` =
  exampleProject("zio-app")
    .dependsOn(core, `zio-ext`, `examples-app-data`)

lazy val `examples-app-data` =
  exampleProject("app-data")
    .dependsOn(core)

def mkProject(id: String, baseDir: Option[String] = None) =
  Project(s"influxdb-$id", file(id))
    .settings(
      libraryDependencies ++= List(        
        cats.core,
        specs2.cats          % Test,
        specs2.core          % Test,
        specs2.discipline    % Test,
        specs2.scalaCheck    % Test,
        ScalaCheck.core      % Test,
      )
    )

def exampleProject(id: String) =
  Project(s"influxdb-examples-$id", file(s"examples/$id"))
    .settings(
      publish         := {},
      publishLocal    := {},
      publishArtifact := false,
      libraryDependencies ++= List(
        cats.core,
        "org.slf4j"      %  "slf4j-api"       % "1.7.30",
        "ch.qos.logback" %  "logback-classic" % "1.2.3"
      )
    )
