import Dependencies._

name := "influx"

inThisBuild(
  List(
    organization := "io.github.anakos",
    scalaVersion := "2.13.1",
    addCompilerPlugin("org.typelevel" % "kind-projector_2.13.1" % "0.11.0"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
  )
)

lazy val root = (project in file("."))
  .settings(
    publish         := {},
    publishLocal    := {},
    publishArtifact := false,
    // libraryDependencies ++= List(
    //   "org.asynchttpclient" % "async-http-client" % "2.11.0",
    //    cats.core,
    //    cats.effect,
    //    has,
    //    circe.core,
    //    circe.generic,
    //    circe.jawn,
    //    cats.laws            % "it,test",
    //    specs2.cats          % "it,test",
    //    specs2.core          % "it,test",
    //    specs2.discipline    % "it,test",
    //    specs2.scalaCheck    % "it,test",
    //    ScalaCheck.core      % "it,test",
    //    ScalaCheck.shapeless % "it,test",
    //    "com.github.tomakehurst" % "wiremock" % "2.26.3" % Test
    // )
  )
  .aggregate(core, `cats-ext`)

lazy val core =
  mkProject("core")
    .settings(
      libraryDependencies ++= List(
        "org.asynchttpclient" % "async-http-client" % "2.11.0",
        circe.core,
        circe.generic,
        circe.jawn,
        cats.laws                % Test,
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