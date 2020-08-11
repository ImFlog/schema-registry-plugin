buildscript {

    repositories {
        jcenter()
        maven {
            url = uri("http://packages.confluent.io/maven/")
        }
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }

    dependencies {
        classpath("io.confluent:kafka-avro-serializer:5.4.0")
    }
}

plugins {
    id("com.github.imflog.kafka-schema-registry-gradle-plugin")
}

schemaRegistry {
    url.set("http://localhost:8081")

    register {
        subject("foo", "schemas/foo.avsc")
    }

    config {
        subject("foo", "FULL_TRANSITIVE")
    }

    download {
        subject("foo", "schemas/downloaded")
    }

    compatibility {
        subject("foo", "schemas/foo_v2.avsc")
    }
}
