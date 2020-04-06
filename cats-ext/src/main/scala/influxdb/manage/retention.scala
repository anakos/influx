package influxdb
package manage

import cats.data._
import cats.effect._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.option._
import influxdb.http.api
import influxdb.query
import io.circe.generic.auto._

// // Database
object retention {
  def createPolicy[E : influxdb.Has](params: Params) =
    validate(params.buildCreatePolicy())
      .flatMap(query.single[E, api.Statement](_))
    
  def showPolicies[E : influxdb.Has](params: Params) =
    query.series[E](params.buildShowPolicy())

  def dropPolicy[E : influxdb.Has](params: Params) =
    validate(params.buildDropPolicy())
      .flatMap { exec[E, api.Statement](_) }

  def alterPolicy[E : influxdb.Has](params: Params) =
    validate(params.buildAlterPolicy())
      .flatMap(query.series[E](_))    

  private def validate[E, A](x: EitherNel[String, A]): RIO[E, A] =
    ReaderT.liftF(
      IO.fromEither(
        x.leftMap(errs => InfluxException.ClientError(s"cmd validation failed with the following errors: ${errs.toList.mkString("|")}"))
      )
    )

  final class Natural private(val value: Int) {
    override def toString(): String =
      value.toString()
  }
  object Natural {
    def create(num: Int): Option[Natural] =
      if (num < 0) None
      else Some(new Natural(num))
  }

  final case class Params(dbName: String, name: Option[String], duration: Option[String], replication: Option[Natural], defaultText: Option[String]) { self =>
    def withReplication(value: Natural): Params =
      self.copy(replication = value.some)
    def withDuration(value: String): Params =
      self.copy(duration = value.some)
    def withPolicyName(value: String): Params =
      self.copy(name = value.some)
    def useDefault(): Params =
      self.copy(defaultText = "DEFAULT".some)

    def buildCreatePolicy(): EitherNel[String, query.Params] =
      (name.toValidNel("policy name was not defined"),
        duration.toValidNel("duration was not defined"),
        replication.toValidNel("replication was not defined"))
        .mapN { (n, d, r) =>
          query.Params.singleQuery(
            applyDefaultText(s"""CREATE RETENTION POLICY "$n" ON "$dbName" DURATION $d REPLICATION $r""")
          )
        }
        .toEither  

    def buildShowPolicy(): query.Params =
      query.Params.singleQuery(s"""SHOW RETENTION POLICIES ON "$dbName"""")

    def buildAlterPolicy(): EitherNel[String, query.Params] = {
      // if (duration == null && replication == -1 && !default)
      val defaultRequirements = defaultText match {
        case None if duration.isEmpty && replication.isEmpty =>
          Validated.invalidNel("at least one parameter has to be set")
        case _ => Validated.validNel(())
      }

      val requireName = name
        .toValidNel("name is required to alter retention policy")
        .map { n => s"""ALTER RETENTION POLICY "$n" ON "$dbName"""" }

      (defaultRequirements, requireName).mapN { (_, base) =>
        val result = duration.fold(base) { d => s"$base DURATION $d" }
        query.Params.singleQuery(
          applyDefaultText(
            replication.fold(result) { r => s"$result REPLICATION $r" }
          )
        )
      }
      .toEither
    }
      
    def buildDropPolicy(): EitherNel[String, String] =
      name.toRightNel("policy name is required by DROP command")
        .map { n => s"""DROP RETENTION POLICY "$n" ON "$dbName"""" }

    private def applyDefaultText(cmd: String): String =
      defaultText.fold(cmd) { x => s"$cmd $x" }
  }
  object Params {
    def create(dbName: String) = Params(dbName, none, none, none, none)
  }
}