import sbt._

object Dependencies {
  object cats {
    def mkModule(name: String) =
      "org.typelevel" %% s"cats-${name}" % "2.1.1" withSources()

    val core       = mkModule("core")
    val effect     = "org.typelevel" %% "cats-effect" % "2.1.2" withSources()
    val laws       = mkModule("laws")
    val scalaCheck = "io.chrisdavenport" %% "cats-scalacheck" % "0.2.0"
  }  

  object circe {
    def mkModule(name: String) =
      "io.circe" %% s"circe-${name}" % "0.13.0"

    val core    = mkModule("core")
    val generic = mkModule("generic")
    val testing = mkModule("testing")
    val jawn    = mkModule("jawn")
    val fs2     = mkModule("fs2")
  }

  object fs2 {
    def mkModule(name: String) =
      "co.fs2" %% s"fs2-$name" % "2.3.0"

    val core = mkModule("core")
    val io   = mkModule("io")
  }

  val has = "io.github.anakos" %% "data-has" % "0.1.1" 

  object ScalaCheck {
    val core      = "org.scalacheck" %% "scalacheck" % "1.14.3"
  }

  object specs2 {
    def mkModule(name: String) =
      "org.specs2" %% s"specs2-${name}" % "4.8.3" excludeAll(
        ExclusionRule(organization = "org.typelevel")
      )

    val cats       = mkModule("cats")
    val core       = mkModule("core")
    val discipline = "org.typelevel" %% "discipline-specs2" % "1.0.0"
    val scalaCheck = mkModule("scalacheck")
  }


  object sttp {
    def mkModule(name: String) =
      "com.softwaremill.sttp.client" %% name % "2.0.7"

    val core              = mkModule("core")
    val async_client      = mkModule("async-http-client-backend")
    val async_client_cats = mkModule("async-http-client-backend-cats")
    val async_client_fs2  = mkModule("async-http-client-backend-fs2")
    
  }
}
