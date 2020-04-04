import sbt._

object Dependencies {
  object cats {
    def mkModule(name: String) =
      "org.typelevel" %% s"cats-${name}" % "2.1.1" withSources()

    val core   = mkModule("core")
    val effect = "org.typelevel" %% "cats-effect" % "2.1.2" withSources()
    val laws   = mkModule("laws")
  }  

  object circe {
    def mkModule(name: String) =
      "io.circe" %% s"circe-${name}" % "0.13.0"

    val core       = mkModule("core")
    val generic    = mkModule("generic")
    val testing    = mkModule("testing")
    val jawn       = mkModule("jawn")
  }

  val has = "io.github.anakos" %% "data-has" % "0.1.0" 

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
}
