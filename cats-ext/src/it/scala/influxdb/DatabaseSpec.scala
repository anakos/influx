package influxdb

import cats.syntax.apply._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._

import influxdb.http.Handle
import influxdb.manage._
import influxdb.query.{DB => ReadDB}
import influxdb.write.{DB => WriteDB}
import influxdb.write._
import influxdb.write.Parameter.{Consistency, Precision}

import org.specs2.mutable

import scala.concurrent.duration._

class DatabaseSpec extends mutable.Specification with InfluxDbContext[Handle] {
  sequential
  
  override val dbName = "_test_database_db"

  override val env = Handle.create(defaultConfig())

  "manage.db" >> {
    "show existing database" >> { handle: Handle =>
      withDb((db.exists[Handle](dbName), db.show[Handle]()).tupled)
        .run(handle).unsafeRunSync() must beLike {
          case (true, names) =>
            names must contain(DbName(dbName))
        }
    }
  }
  
  "manage.users" >> {
    val username = "_test_username"
    val password = "test_password"

    "create and drop" >> { handle: Handle =>
      val result = for {
        _    <- users.create[Handle](username, password)
        pre  <- users.showUsers[Handle]()
        _    <- users.dropUser[Handle](username) 
        post <- users.showUsers[Handle]()
      } yield (pre, post)
    
      result.run(handle).unsafeRunSync() must beLike {
        case (pre, post) =>
          pre.map(_.name) must contain(username)
          post.map(_.name) must not contain(username) 
      }
    }

    "create cluster admin" >> { handle: Handle =>
      val result = for {
        _       <- users.createClusterAdmin[Handle](username, password)
        isAdmin <- users.userIsClusterAdmin[Handle](username)
        _       <- users.dropUser[Handle](username) 
      } yield isAdmin
    
      result.run(handle).unsafeRunSync() must beLike {
        case isAdmin => isAdmin must beTrue 
      }
    }

    "make cluster admin" >> { handle: Handle =>
      val result = for {
        _           <- users.create[Handle](username, password)
        isAdminPre  <- users.userIsClusterAdmin[Handle](username)
        _           <- users.makeClusterAdmin[Handle](username)
        isAdminPost <- users.userIsClusterAdmin[Handle](username)
        _           <- users.dropUser[Handle](username) 
      } yield (isAdminPre, isAdminPost)
    
      result.run(handle).unsafeRunSync() must beLike {
        case (isAdminPre, isAdminPost) =>
          isAdminPre must beFalse
          isAdminPost must beTrue 
      }
    }

    "change passwords" >> { handle: Handle =>
      (users.create[Handle](username, password) >>
        users.setUserPassword[Handle](username, "new_password") >>
        users.dropUser[Handle](username)).run(handle).attempt.unsafeRunSync() must beRight
    }

    "grant/revoke user Privileges can be granted to and revoked from a user" >> { handle: Handle =>
      (db.create[Handle](dbName) >>
        users.create[Handle](username, password) >>
        users.grantPrivileges[Handle](username, dbName, ALL) >>
        users.revokePrivileges[Handle](username, dbName, WRITE) >>
        db.drop[Handle](dbName)).run(handle).attempt.unsafeRunSync() must beRight
    }

    "passwords are correctly escaped" >> {
      users.escapePassword("pass'wor\nd") must_=== "pass\\'wor\\\nd"
    }
  }

  "query" >> {
    "batch queries can be executed at the same time" >> { handle: Handle =>
      val query1     = "select * from subscriber limit 5"
      val query2     = """select * from "write" limit 5"""
      val internalDB = "_internal"
      
      (ReadDB.query[Handle, MultiQueryExample](query.Params.multiQuery(List(query1, query2), internalDB)),
          ReadDB.query[Handle, SubscriberInternal](query.Params.singleQuery(query1, internalDB)),
          ReadDB.query[Handle, WriteInternal](query.Params.singleQuery(query2, internalDB)))
        .tupled
        .run(handle).unsafeRunSync() must beLike {
        case (combined, results1, results2) =>
          combined.size must_=== (results1.size + results2.size)
      }
    }    
  }

  "write" >> {
    "to a non-existent database yields DatabaseNotFoundException" >> { handle: Handle =>
      WriteDB.write[Handle](
        write.Params.default(dbName, Point.withDefaults("test_measurement").addField("value", 123))
      )
      .run(handle)
      .attempt
      .unsafeRunSync must beLeft.like {
        case InfluxException.ClientError(_) => true
      }
    }

    "single point" >> { handle: Handle =>
      val action =
        WriteDB.write[Handle](write.Params.default(dbName, Point.withDefaults("test_measurement").addField("value", 123))) >>
          ReadDB.query[Handle, TestMeasurement](query.Params.singleQuery("SELECT * FROM test_measurement", dbName))

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case series => series must have size 1
      }
    }

    "single point with tags" >> { handle: Handle =>
      val withTag = Point.withDefaults("test_measurement").addField("value", 123).addTag("tag_key", "tag_value")
      val action  =
        WriteDB.write[Handle](write.Params.default(dbName, withTag)) >>
          ReadDB.query[Handle, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement WHERE tag_key='tag_value'", dbName)
          )

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case series => series must have size 1
      }
    }

    "single point with a precision parameter" >> { handle: Handle =>
      val time  = 1444760421270L
      val point = Point.withDefaults("test_measurement", time).addField("value", 123)
      val action =
        WriteDB.write[Handle](write.Params.default(dbName, point).withPrecision(Precision.MILLISECONDS)) >>
          ReadDB.query[Handle, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement", dbName, Precision.MILLISECONDS)
          )
            
      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(elem) =>
          elem.time.toEpochMilli() must_=== time
      }
    }

    "single point with a consistency parameter" >> { handle: Handle =>
      val point  = Point.withDefaults("test_measurement").addField("value", 123)
      val action =
        WriteDB.write[Handle](write.Params.default(dbName, point).withConsistency(Consistency.ALL)) >>
          ReadDB.query[Handle, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
          )
            
      withDb(action).run(handle).unsafeRunSync() must beLike {
        case series => series must have size 1
      }
    }

    "single point with a retention policy parameter" >> { handle: Handle =>
      val retentionPolicyName = "custom_retention_policy"
      val measurementName     = "test_measurement"
      val point               = Point.withDefaults(measurementName).addField("value", 123)
      val action              = for {
        _      <- retention.createPolicy[Handle](
          retention.Params
            .create(dbName)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
            .withReplication(Natural.create(1).get)
        )
        _      <- WriteDB.write[Handle](write.Params.default(dbName, point).withRetentionPolicy(retentionPolicyName))
        result <- ReadDB.query[Handle, TestMeasurement](
          query.Params.singleQuery(
            s"SELECT * FROM ${retentionPolicyName}.${measurementName}", dbName
          )
        )
      } yield result 
      
      withDb(action).run(handle).unsafeRunSync must beLike {
        case series => series must have size 1
      }
    }

    "fails when non-existent retention policy is specified" >> { handle: Handle =>
      val params  = write.Params.default(
        dbName,
        Point.withDefaults("test_measurement").addField("value", 123)
      ).withRetentionPolicy("fake_retention_policy")

      (db.create[Handle](dbName) >> WriteDB.write[Handle](params).attempt)
        .run(handle)
        .unsafeRunSync() must beLeft
    }

    "multiple points" >> { handle: Handle =>
      val time   = 1444760421000L
      val points = List(
        Point.withDefaults("test_measurement", time).addField("value", 123),
        Point.withDefaults("test_measurement", time + 1).addField("value", 123),
        Point.withDefaults("test_measurement", time + 2).addField("value", 123)
      )

      val action =
        WriteDB.write[Handle](write.Params.bulk(dbName, points)) >>
          ReadDB.query[Handle, TestMeasurement](
            query.Params.singleQuery("SELECT * FROM test_measurement", dbName)
          )

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case series => series must have size 3
      }
    }
  }

  "retention" >> {
    val retentionPolicyName = "test_retention_policy"

    "create policy (default)" >> { handle: Handle =>
      val action =
        retention.createPolicy[Handle](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
            .useDefault()
        ) >> retention.showPolicies[Handle](retention.Params.create(dbName))

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(_, RetentionPolicy.DefaultPolicy(name, duration, _, replicaN)) =>
          name must_=== retentionPolicyName
          duration must_=== 168.hours
          replicaN must_=== 1
      }
    }

    "create and delete policy" >> { handle: Handle =>
      val action = for {
        _   <- retention.createPolicy[Handle](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        pre  <- retention.showPolicies[Handle](retention.Params.create(dbName))
        _    <- retention.dropPolicy[Handle](retention.Params.create(dbName).withPolicyName(retentionPolicyName))
        post <- retention.showPolicies[Handle](retention.Params.create(dbName))
      } yield (pre, post)

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case (pre, post) =>
          pre must have size 2
          post must have size 1
      }
    }

    "alter policy duration" >> { handle: Handle =>
      val action = for {
        _      <- retention.createPolicy[Handle](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        _      <- retention.alterPolicy[Handle](
          retention.Params.create(dbName)
            .withPolicyName(retentionPolicyName)
            .withDuration("2w")
        )
        result <- retention.showPolicies[Handle](retention.Params.create(dbName))
      } yield result

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(autogen, policy) =>
          autogen.name must_=== "autogen"
          policy.name must_=== retentionPolicyName
          policy.duration must_=== 336.hours
      }
    }

    "alter policy replication" >> { handle: Handle =>
      val action = for {
        _      <- retention.createPolicy[Handle](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        _      <- retention.alterPolicy[Handle](
          retention.Params.create(dbName)
            .withPolicyName(retentionPolicyName)
            .withReplication(Natural.create(2).get)
        )
        result <- retention.showPolicies[Handle](retention.Params.create(dbName))
      } yield result

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(autogen, policy) =>
          autogen.name must_=== "autogen"
          policy.name must_=== retentionPolicyName
          policy.replicaN must_=== 2
      }            
    }

    "alter policy defaultness" >> { handle: Handle =>
      val action = for {
        _      <- retention.createPolicy[Handle](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        _      <- retention.alterPolicy[Handle](
          retention.Params.create(dbName)
            .withPolicyName(retentionPolicyName)
            .useDefault()
        )
        result <- retention.showPolicies[Handle](retention.Params.create(dbName))
      } yield result

      withDb(action).run(handle).unsafeRunSync() must beLike {
        case Vector(autogen, RetentionPolicy.DefaultPolicy(name,_,_,_)) =>
          autogen.name must_=== "autogen"
          name must_=== retentionPolicyName
      }      
    }

    "at least one parameter has to be altered" >> { handle: Handle =>
      val action = for {
        _      <- retention.createPolicy[Handle](
          retention.Params.create(dbName)
            .withReplication(Natural.create(1).get)
            .withPolicyName(retentionPolicyName)
            .withDuration("1w")
        )
        result <- retention.alterPolicy[Handle](retention.Params.create(dbName).withPolicyName(retentionPolicyName)).attempt
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