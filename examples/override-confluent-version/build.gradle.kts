buildscript {

    repositories {
        mavenCentral()
        maven {
            url = uri("https://packages.confluent.io/maven/")
        }
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }

    dependencies {
        classpath("io.confluent:kafka-schema-registry-parent:5.4.0")
    }
}

plugins {
    id("com.github.imflog.kafka-schema-registry-gradle-plugin")
}

schemaRegistry {
    url.set("http://localhost:8081")

    register {
        subject("company", "schemas/avro/company.avsc")
            .addLocalReference("address", "schemas/avro/location-address.avsc")
    }

    config {
        subject("company", "FULL_TRANSITIVE")
    }

    download {
        subject("company", "schemas/avro/downloaded")
    }

    compatibility {
        subject("company", "schemas/avro/company_v2.avsc")
            .addLocalReference("address", "schemas/avro/location-address.avsc")
    }
}
