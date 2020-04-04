package influxdb.query

import cats.instances.all._
import cats.laws.discipline.MonadTests
import org.scalacheck._
import org.specs2.mutable
import org.typelevel.discipline.specs2.mutable.Discipline

class ResultSpec extends mutable.Specification with Discipline {
  "Result" >> {
    implicit def arbQueryResult[A : Arbitrary]: Arbitrary[Result[A]] =
      Arbitrary(Gen.listOfN(10, Arbitrary.arbitrary[A]).map(Result(_)))

    checkAll("Result", MonadTests[Result].monad[Int, String, Boolean])
  }
}