plugins {
    id("com.github.imflog.kafka-schema-registry-gradle-plugin")
}

schemaRegistry {
    url.set("http://localhost:8081")
    quiet.set(false)
    outputDirectory.set("schemas/avro/results/")

    register {
        subject("company", "schemas/avro/company.avsc", "AVRO")
                .addLocalReference("Address", "schemas/avro/location-address.avsc")
        subject("user", "schemas/avro/user.avsc", "AVRO")
                .addReference("company", "company", -1)
        subject("location-address", "schemas/avro/location-address.avsc", "AVRO")
        subject("location-latlong", "schemas/avro/location-latlong.avsc", "AVRO")
    }

    config {
        subject("company", "FULL_TRANSITIVE")
        subject("user", "FULL_TRANSITIVE")
        subject("location-address", "FULL_TRANSITIVE")
        subject("location-latlong", "FULL_TRANSITIVE")
    }

    download {
        metadata.set(true)
        subject("company", "schemas/avro/downloaded")
        subject("user", "schemas/avro/downloaded")
        subjectPattern("location.*", "schemas/avro/downloaded/location")
    }

    compatibility {
        subject("company", "schemas/avro/company_v2.avsc", "AVRO")
                .addLocalReference("Address", "schemas/avro/location-address.avsc")
        subject("user", "schemas/avro/user.avsc", "AVRO")
                .addReference("company", "company", -1)
        subject("location-address", "schemas/avro/location-address.avsc", "AVRO")
        subject("location-latlong", "schemas/avro/location-latlong.avsc", "AVRO")
    }
}
