import com.github.imflog.schema.registry.tasks.download.MetadataExtension

plugins {
    id("com.github.imflog.kafka-schema-registry-gradle-plugin")
}

schemaRegistry {
    url.set("http://localhost:8081")
    quiet.set(false)
    outputDirectory.set("schemas/avro/results/")
    pretty.set(true)
    failFast.set(true)

    register {
        subject("company", "schemas/avro/company.avsc", "AVRO")
                .addLocalReference("Address", "schemas/avro/location-address.avsc")
        subject("user", "schemas/avro/user.avsc", "AVRO")
                .addReference("company", "company", -1)
        subject("location-address", "schemas/avro/location-address.avsc", "AVRO")
        subject("location-latlong", "schemas/avro/location-latlong.avsc", "AVRO")
        subject("my-record","schemas/avro/my-record.avsc", "AVRO")
            .setMetadata("extra/metadata.json")
            .setRuleSet("extra/ruleSet.json")
            .setNormalized(true)
    }

    config {
        subject("company", "FULL_TRANSITIVE")
        subject("user", "FULL_TRANSITIVE")
        subject("location-address", "FULL_TRANSITIVE")
        subject("location-latlong", "FULL_TRANSITIVE")
    }

    download {
        metadata.set(MetadataExtension(true))
//        subject("company", "schemas/avro/downloaded") // Retrieved via reference from the user subject
        subject("user", "schemas/avro/downloaded", true)
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
