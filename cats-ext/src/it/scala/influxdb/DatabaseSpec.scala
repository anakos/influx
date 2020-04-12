package influxdb

import cats.syntax.apply._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._

import influxdb.manage._
import influxdb.query.{DB => ReadDB}
import influxdb.write.{DB => WriteDB}
import influxdb.write._
import influxdb.write.Parameter.{Consistency, Precision}

import org.specs2.mutable

import scala.concurrent.duration._
import influxdb.query.ChunkSize

class DatabaseSpec extends mutable.Specification with InfluxDbContext[HttpClient] {
  sequential
  
  override val dbName = "_test_database_db"

  override val env = InfluxDB.create(defaultConfig())

  "manage.db" >> {
    "show existing database" >> { client: HttpClient =>
      withDb((db.exists[HttpClient](dbName), db.show[HttpClient]()).tupled)
        .run(client).unsafeRunSync() must beLike {
          case (true, names) =>
            names must contain(DbName(dbName))
        }
    }
  }
  
  "manage.users" >> {
    val username = "_test_username"
    val password = "test_password"

    "create and drop" >> { handle: HttpClient =>
      val result = for {
        _    <- users.create[HttpClient](username, password)
        pre  <- users.showUsers[HttpClient]()
        _    <- users.dropUser[HttpClient](username) 
        post <- users.showUsers[HttpClient]()
      } yield (pre, post)
    
      result.run(handle).unsafeRunSync() must beLike {
        case (pre, post) =>
          pre.map(_.name) must contain(username)
          post.map(_.name) must not contain(username) 
      }
    }

    "create cluster admin" >> { handle: HttpClient =>
      val result = for {
        _       <- users.createClusterAdmin[HttpClient](username, password)
        isAdmin <- users.userIsClusterAdmin[HttpClient](username)
        _       <- users.dropUser[HttpClient](username) 
      } yield isAdmin
    
      result.run(handle).unsafeRunSync() must beLike {
        case isAdmin => isAdmin must beTrue 
      }
    }

    "make cluster admin" >> { handle: HttpClient =>
      val result = for {
        _           <- users.create[HttpClient](username, password)
        isAdminPre  <- users.userIsClusterAdmin[HttpClient](username)
        _           <- users.makeClusterAdmin[HttpClient](username)
        isAdminPost <- users.userIsClusterAdmin[HttpClient](username)
        _           <- users.dropUser[HttpClient](username) 
      } yield (isAdminPre, isAdminPost)
    
      result.run(handle).unsafeRunSync() must beLike {
        case (isAdminPre, isAdminPost) =>
          isAdminPre must beFalse
          isAdminPost must beTrue 
      }
    }

    "change passwords" >> { handle: HttpClient =>
      (users.create[HttpClient](username, password) >>
        users.setUserPassword[HttpClient](username, "new_password") >>
        users.dropUser[HttpClient](username)).run(handle).attempt.unsafeRunSync() must beRight
    }

    "grant/revoke user Privileges can be granted to and revoked from a user" >> { handle: HttpClient =>
      (db.create[HttpClient](dbName) >>
        users.create[HttpClient](username, password) >>
        users.grantPrivileges[HttpClient](username, dbName, ALL) >>
        users.revokePrivileges[HttpClient](username, dbName, WRITE) >>
        db.drop[HttpClient](dbName)).run(handle).attempt.unsafeRunSync() must beRight
    }

    "passwords are correctly escaped" >> {
      users.escapePassword("pass'wor\nd") must_=== "pass\\'wor\\\nd"
    }
  }

  "query" >> {
    "batch queries can be executed at the same time" >> { handle: HttpClient =>
      val query1     = "select * from subscriber limit 5"
      val query2     = """select * from "write" limit 5"""
      val internalDB = "_internal"
      
      (ReadDB.query[HttpClient, MultiQueryExample](query.Params.multiQuery(List(query1, query2), internalDB)),
          ReadDB.query[HttpClient, SubscriberInternal](query.Params.singleQuery(query1, internalDB)),
          ReadDB.query[HttpClient, WriteInternal](query.Params.singleQuery(query2, internalDB)))
        .tupled
        .run(handle).unsafeRunSync() must beLike {
        case (combined, results1, results2) =>
          combined.size must_=== (results1.size + results2.size)
      }
    }
    
    "chunked queries yield same data as non-chunked variants" >> { handle: HttpClient =>
      val action = for {
        _         <- WriteDB.write[HttpClient](
          write.Params.bulk(
              dbName,
              (0L to 10000).map { x => Point.withDefaults("test_measurement").addField("value", x) }.toList
          )
        )
        unchunked <- ReadDB.query[HttpClient, TestMeasurement](
          query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
        )
        chunked   <- ReadDB.queryChunked[HttpClient, TestMeasurement](
          query.Params.singleQuery("SELECT * FROM test_measurement", dbName),
          ChunkSize.withSize(Natural.create(1).get)
        ).compile.toVector
      } yield (unchunked, chunked)

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case (unchunked, chunked) =>
          unchunked must_=== chunked
      }
    }
  }

  "write" >> {
    "to a non-existent database yields DatabaseNotFoundException" >> { handle: HttpClient =>
      WriteDB.write[HttpClient](
        write.Params.default(dbName, Point.withDefaults("test_measurement").addField("value", 123))
      )
      .run(handle)
      .attempt
      .unsafeRunSync must beLeft.like {
        case InfluxException.ClientError(_) => true
      }
    }

    "single point" >> { handle: HttpClient =>
      val action =
        WriteDB.write[HttpClient](write.Params.default(dbName, Point.withDefaults("test_measurement").addField("value", 123))) >>
          ReadDB.query[HttpClient, TestMeasurement](query.Params.singleQuery("SELECT * FROM test_measurement", dbName))

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case series => series must have size 1
      }
    }

    "single point with tags" >> { handle: HttpClient =>
      val withTag = Point.withDefaults("test_measurement").addField("value", 123).addTag("tag_key", "tag_value")
      val action  =
        WriteDB.write[HttpClient](write.Params.default(dbName, withTag)) >>
          ReadDB.query[HttpClient, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement WHERE tag_key='tag_value'", dbName)
          )

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case series => series must have size 1
      }
    }

    "single point with a precision parameter" >> { handle: HttpClient =>
      val time  = 1444760421270L
      val point = Point.withDefaults("test_measurement", time).addField("value", 123)
      val action =
        WriteDB.write[HttpClient](write.Params.default(dbName, point).withPrecision(Precision.MILLISECONDS)) >>
          ReadDB.query[HttpClient, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement", dbName, Precision.MILLISECONDS)
          )
            
      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(elem) =>
          elem.time.toEpochMilli() must_=== time
      }
    }

    "single point with a consistency parameter" >> { handle: HttpClient =>
      val point  = Point.withDefaults("test_measurement").addField("value", 123)
      val action =
        WriteDB.write[HttpClient](write.Params.default(dbName, point).withConsistency(Consistency.ALL)) >>
          ReadDB.query[HttpClient, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
          )
            
      withDb(action).run(handle).unsafeRunSync() must beLike {
        case series => series must have size 1
      }
    }

    "single point with a retention policy parameter" >> { handle: HttpClient =>
      val retentionPolicyName = "custom_retention_policy"
      val measurementName     = "test_measurement"
      val point               = Point.withDefaults(measurementName).addField("value", 123)
      val action              = for {
        _      <- retention.createPolicy[HttpClient](
          retention.Params
            .create(dbName)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
            .withReplication(Natural.create(1).get)
        )
        _      <- WriteDB.write[HttpClient](write.Params.default(dbName, point).withRetentionPolicy(retentionPolicyName))
        result <- ReadDB.query[HttpClient, TestMeasurement](
          query.Params.singleQuery(
            s"SELECT * FROM ${retentionPolicyName}.${measurementName}", dbName
          )
        )
      } yield result 
      
      withDb(action).run(handle).unsafeRunSync must beLike {
        case series => series must have size 1
      }
    }

    "fails when non-existent retention policy is specified" >> { handle: HttpClient =>
      val params  = write.Params.default(
        dbName,
        Point.withDefaults("test_measurement").addField("value", 123)
      ).withRetentionPolicy("fake_retention_policy")

      (db.create[HttpClient](dbName) >> WriteDB.write[HttpClient](params).attempt)
        .run(handle)
        .unsafeRunSync() must beLeft
    }

    "multiple points" >> { handle: HttpClient =>
      val time   = 1444760421000L
      val points = List(
        Point.withDefaults("test_measurement", time).addField("value", 123),
        Point.withDefaults("test_measurement", time + 1).addField("value", 123),
        Point.withDefaults("test_measurement", time + 2).addField("value", 123)
      )

      val action =
        WriteDB.write[HttpClient](write.Params.bulk(dbName, points)) >>
          ReadDB.query[HttpClient, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
          )

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case series => series must have size 3
      }
    }
  }

  "retention" >> {
    val retentionPolicyName = "test_retention_policy"

    "create policy (default)" >> { handle: HttpClient =>
      val action =
        retention.createPolicy[HttpClient](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
            .useDefault()
        ) >> retention.showPolicies[HttpClient](retention.Params.create(dbName))

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(_, RetentionPolicy.DefaultPolicy(name, duration, _, replicaN)) =>
          name must_=== retentionPolicyName
          duration must_=== 168.hours
          replicaN must_=== 1
      }
    }

    "create and delete policy" >> { handle: HttpClient =>
      val action = for {
        _   <- retention.createPolicy[HttpClient](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        pre  <- retention.showPolicies[HttpClient](retention.Params.create(dbName))
        _    <- retention.dropPolicy[HttpClient](retention.Params.create(dbName).withPolicyName(retentionPolicyName))
        post <- retention.showPolicies[HttpClient](retention.Params.create(dbName))
      } yield (pre, post)

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case (pre, post) =>
          pre must have size 2
          post must have size 1
      }
    }

    "alter policy duration" >> { handle: HttpClient =>
      val action = for {
        _      <- retention.createPolicy[HttpClient](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        _      <- retention.alterPolicy[HttpClient](
          retention.Params.create(dbName)
            .withPolicyName(retentionPolicyName)
            .withDuration("2w")
        )
        result <- retention.showPolicies[HttpClient](retention.Params.create(dbName))
      } yield result

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(autogen, policy) =>
          autogen.name must_=== "autogen"
          policy.name must_=== retentionPolicyName
          policy.duration must_=== 336.hours
      }
    }

    "alter policy replication" >> { handle: HttpClient =>
      val action = for {
        _      <- retention.createPolicy[HttpClient](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        _      <- retention.alterPolicy[HttpClient](
          retention.Params.create(dbName)
            .withPolicyName(retentionPolicyName)
            .withReplication(Natural.create(2).get)
        )
        result <- retention.showPolicies[HttpClient](retention.Params.create(dbName))
      } yield result

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(autogen, policy) =>
          autogen.name must_=== "autogen"
          policy.name must_=== retentionPolicyName
          policy.replicaN must_=== 2
      }            
    }

    "alter policy defaultness" >> { handle: HttpClient =>
      val action = for {
        _      <- retention.createPolicy[HttpClient](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        _      <- retention.alterPolicy[HttpClient](
          retention.Params.create(dbName)
            .withPolicyName(retentionPolicyName)
            .useDefault()
        )
        result <- retention.showPolicies[HttpClient](retention.Params.create(dbName))
      } yield result

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(autogen, RetentionPolicy.DefaultPolicy(name,_,_,_)) =>
          autogen.name must_=== "autogen"
          name must_=== retentionPolicyName
      }      
    }

    "at least one parameter has to be altered" >> { handle: HttpClient =>
      val action = for {
        _      <- retention.createPolicy[HttpClient](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        result <- retention.alterPolicy[HttpClient](retention.Params.create(dbName).withPolicyName(retentionPolicyName)).attempt
      } yield result

      withDb(action).run(handle).unsafeRunSync() must beLeft.like {
        case InfluxException.ClientError(msg) =>
          msg must contain("at least one parameter has to be set")
      }
    }
  }
}

//curl -vvv -XPOST "http://localhost:8086/query?u=influx_user&p=influx_password&db=_test_database_db" --data-urlencode "q=SELECT * FROM test_measurement"
//curl "http://localhost:8086/query?u=influx_user&p=influx_password&db=mydb" --data-urlencode "q=SHOW RETENTION POLICIES"
// curl -vvv -XPOST "http://localhost:8086/query?u=influx_user&p=influx_password&db=_test_database_db" --data-urlencode "q=SELECT * FROM test_measurement"

// curl -vvv -XPOST "http://localhost:8086/write?u=influx_user&p=influx_password&db=_test_database_db" --data-binary 'test_measurement,tag_key=tag_value value=123 1434055562000000000'

// curl -i -XPOST 'http://localhost:8086/write?db=mydb' --data-binary 'cpu_load_short,host=server01,region=us-west value=0.64 1434055562000000000'


// curl -vvv -XPOST "http://localhost:8086/query?u=influx_user&p=influx_password" --data-urlencode "q=CREATE DATABASE mydb"
/**
 * TODO:
 * consider the following errors:
 * {"results":[{"statement_id":0,"error":"database not found: mydb"}]}
 * */