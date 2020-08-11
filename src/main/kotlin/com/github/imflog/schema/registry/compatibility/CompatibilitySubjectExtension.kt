package com.github.imflog.schema.registry.compatibility

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty


// TODO : Add tests for JSON / PROTOBUF
open class CompatibilitySubjectExtension(objects: ObjectFactory) {
    val subjects: ListProperty<CompatibilitySubject> = objects.listProperty(CompatibilitySubject::class.java)

    fun subject(inputSubject: String, file: String) {
        subject(inputSubject, file, AvroSchema.TYPE)
    }

    fun subject(
        inputSubject: String,
        file: String,
        type: String = AvroSchema.TYPE,
        vararg dependencies: SchemaReference
    ) {
        subjects.add(CompatibilitySubject(inputSubject, file, type, dependencies.toList()))
    }
}

data class CompatibilitySubject(
    val inputSubject: String,
    val file: String,
    val type: String,
    val dependencies: List<SchemaReference>
)
