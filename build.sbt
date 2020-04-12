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
  .aggregate(core, `cats-ext`, examples)

lazy val core =
  mkProject("core")
    .settings(
      libraryDependencies ++= List(
        "org.asynchttpclient"   % "async-http-client" % "2.11.0",
        "org.tpolecat"          %% "atto-core"        % "0.7.2",
        circe.core,
        circe.generic,
        circe.jawn,
        circe.fs2,
        sttp.core,
        sttp.async_client,
        sttp.async_client_cats,
        sttp.async_client_fs2,
        cats.laws                % Test,
        cats.scalaCheck          % Test,
        "com.github.tomakehurst" % "wiremock" % "2.26.3" % Test
      )
    )
    
lazy val `cats-ext` =
  mkProject("cats-ext")
    .configs(IntegrationTest)
    .settings(
      Defaults.itSettings,
      libraryDependencies ++= List(
        has,
        specs2.cats % IntegrationTest,
        specs2.core % IntegrationTest,
      )
    ).dependsOn(core)

lazy val examples =
  mkProject("examples")
    .settings(
      publish         := {},
      publishLocal    := {},
      publishArtifact := false,
      libraryDependencies ++= List(
        "org.slf4j"      %  "slf4j-api"       % "1.7.30",
        "ch.qos.logback" %  "logback-classic" % "1.2.3",
        fs2.core,
        fs2.io
      )
    )
    .dependsOn(core, `cats-ext`)

def mkProject(id: String) =
  Project(s"influxdb-$id", file(s"$id"))
    .settings(
      libraryDependencies ++= List(        
        cats.core,
        cats.effect,        
        specs2.cats          % Test,
        specs2.core          % Test,
        specs2.discipline    % Test,
        specs2.scalaCheck    % Test,
        ScalaCheck.core      % Test,
      )
    )
