package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.MixedReferenceException
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.toSchemaType
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
        val subject = RegisterSubject(inputSubject, file, type.toSchemaType())
        subjects.add(subject)
        return subject
    }
}

data class RegisterSubject(
    val inputSubject: String,
    val file: String,
    val type: SchemaType,
    val references: MutableList<SchemaReference> = mutableListOf(),
    val localReferences: MutableList<LocalReference> = mutableListOf()
) {
    fun addReference(name: String, subject: String, version: Int): RegisterSubject {
        if (localReferences.isNotEmpty()) throw MixedReferenceException()
        references.add(SchemaReference(name, subject, version))
        return this
    }

    fun addLocalReference(name: String, path: String): RegisterSubject {
        if (references.isNotEmpty()) throw MixedReferenceException() // We can probably lift this constraint
        localReferences.add(LocalReference(name, path))
        return this
    }
}
