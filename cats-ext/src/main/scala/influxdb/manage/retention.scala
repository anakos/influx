package influxdb
package manage

import cats.data._
import cats.effect._
import cats.instances.all._
import cats.syntax.all._
import influxdb.query.{DB, FieldValidator, QueryResults}
import influxdb.query.types._
import scala.concurrent.duration._
import scala.collection.immutable.ListMap

// Database
object retention {
  def createPolicy[E : influxdb.Has](params: Params) =
    validate(params.buildCreatePolicy())
      .flatMap(DB.query_[E](_))
    
  def showPolicies[E : influxdb.Has](params: Params) =
    DB.query[E, RetentionPolicy](params.buildShowPolicy())

  def dropPolicy[E : influxdb.Has](params: Params) =
    validate(params.buildDropPolicy())
      .flatMap { exec[E](_) }

  def alterPolicy[E : influxdb.Has](params: Params) =
    validate(params.buildAlterPolicy())
      .flatMap(DB.query_[E](_))    

  private def validate[E, A](x: EitherNel[String, A]): RIO[E, A] =
    ReaderT.liftF(
      IO.fromEither(
        x.leftMap(errs => InfluxException.ClientError(s"cmd validation failed with the following errors: ${errs.toList.mkString("|")}"))
      )
    )

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

sealed abstract class RetentionPolicy(
    val name: String, 
    val duration: FiniteDuration,
    val shardGroupDuration: FiniteDuration,
    val replicaN : Int
) extends Product with Serializable
object RetentionPolicy {
  final case class DefaultPolicy(override val name: String, override val duration: FiniteDuration, override val shardGroupDuration: FiniteDuration, override val replicaN : Int) extends RetentionPolicy(name, duration, shardGroupDuration, replicaN)
  final case class Policy(override val name: String, override val duration: FiniteDuration, override val shardGroupDuration: FiniteDuration, override val replicaN : Int) extends RetentionPolicy(name, duration, shardGroupDuration, replicaN)
  
  implicit val parser: QueryResults[RetentionPolicy] =
    new QueryResults[RetentionPolicy] {
      def parseWith(_precision: Option[Precision],
                    name  : Option[String],
                    tags  : ListMap[String, Value],
                    fields: ListMap[String, Nullable]): Either[String, RetentionPolicy] =
        (FieldValidator.byName("name") { _.asString() },
          FieldValidator.byName("duration") { _.asString() }.flatMapF(DurationLiteral.parse(_)),
          FieldValidator.byName("shardGroupDuration") { _.asString() }.flatMapF(DurationLiteral.parse(_)),
          FieldValidator.byName("replicaN") { _.asNum().flatMap(x => Either.catchNonFatal(x.toIntExact).toOption) },
          FieldValidator.byName("default") { _.asBool() }).mapN { 
            case (name, dur, sg, rep, true) => DefaultPolicy(name, dur.unwrap, sg.unwrap, rep)
            case (name, dur, sg, rep, _)    => Policy(name, dur.unwrap, sg.unwrap, rep)
          }
          .run(fields)

    }
}