package com.github.imflog.schema.registry.tasks.register

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

open class RegisterSubjectExtension(objects: ObjectFactory) {
    companion object {
        const val EXTENSION_NAME = "register"
    }

    val subjects: ListProperty<RegisterSubject> = objects.listProperty(RegisterSubject::class.java)

    fun subject(inputSubject: String, file: String) = subject(inputSubject, file, AvroSchema.TYPE)

    fun subject(
        inputSubject: String,
        file: String,
        type: String
    ): RegisterSubject {
        val subject = RegisterSubject(inputSubject, file, type)
        subjects.add(subject)
        return subject
    }
}

data class RegisterSubject(
    val inputSubject: String,
    val file: String,
    val type: String,
    val references: MutableList<SchemaReference> = mutableListOf(),
    val localReference: MutableMap<String, String> = mutableMapOf(),
) {
    fun addReference(name: String, subject: String, version: Int): RegisterSubject {
        references.add(SchemaReference(name, subject, version))
        return this
    }

    fun addLocalReference(name: String, path: String): RegisterSubject {
        localReference[name] = path
        return this
    }
}
