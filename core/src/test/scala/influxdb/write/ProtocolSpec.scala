package influxdb.write

import org.specs2.mutable

class ProtocolSpec extends mutable.Specification {
  "Point" >> {
    "serialize minimal point correctly" >> {
      val point = Point.withDefaults("key")
      point.serialize() must_=== "key"
    }

    "serialize complete point correctly" >> {
      val point = Point.withDefaults("measurement", 1234567890L)
        .addTag("tag_key2", "tag_value2")
        .addTag("tag_key1", "tag_value1")
        .addField("field_key5", BigDecimal("51.98890310"))
        .addField("field_key4", 12.34)
        .addField("field_key3", true)
        .addField("field_key2", 2)
        .addField("field_key1", "field_value1")

      point.serialize() must_=== "measurement,tag_key1=tag_value1,tag_key2=tag_value2 field_key1=\"field_value1\",field_key2=2i,field_key3=true,field_key4=12.34,field_key5=51.98890310 1234567890"
    }
  }

  "Tag" >> {
    "cannot be created with null values" >> {
      Tag.mk("key", null) must beLeft[String]
    }

    "cannot contain empty values" >> {
      Tag.mk("key", "") must beLeft[String]
    }

    "serialized correctly when valid" >> {
      Tag.mk("key", "value").map { _.serialize() } must beRight[String].like {
        case value => value must_=== "key=value"
      }
    }

    "escaped correctly when serialized" >> {
      Tag.mk("ke y", "va lue").map { _.serialize() } must beRight[String].like {
        case value => value must_=== "ke\\ y=va\\ lue"
      }

      Tag.mk("ke,y", "va,lue").map { _.serialize() } must beRight[String].like {
        case value => value must_=== "ke\\,y=va\\,lue"
      }

      Tag.mk("ke=y", "va=lue").map { _.serialize() } must beRight[String].like {
        case value => value must_=== "ke\\=y=va\\=lue"
      }
    }
  }

  "Field" >> {
    "string is serialized correctly" >> {
      Field.String("key", "value").serialize() must_=== "key=\"value\""
    }

    "double is serialized correctly" >> {
      Field.Double("key", 12.123).serialize() must_=== "key=12.123"
    }

    "long is serialized correctly" >> {
      Field.Long("key", 12123L).serialize() must_=== "key=12123i"
    }

    "boolean is serialized correctly" >> {
      Field.Boolean("key", value = true).serialize() must_=== "key=true"
    }

    "big decimal is serialized correctly" >> {
      Field.BigDecimal("key", BigDecimal("51.98890310")).serialize() must_=== "key=51.98890310"
    }

    "escaped correctly" >> {
      Field.String("ke y", "a v=al\"ue").serialize() must_=== "ke\\ y=\"a v=al\\\"ue\""
      Field.Long("key,", 12123L).serialize() must_=== "key\\,=12123i"
    }
  }
}
