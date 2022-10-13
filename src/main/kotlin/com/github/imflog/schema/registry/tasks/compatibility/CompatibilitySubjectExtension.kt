package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.MixedReferenceException
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.toSchemaType
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

open class CompatibilitySubjectExtension(objects: ObjectFactory) {
    companion object {
        const val EXTENSION_NAME = "compatibility"
    }

    val subjects: ListProperty<CompatibilitySubject> = objects.listProperty(CompatibilitySubject::class.java)

    fun subject(inputSubject: String, file: String) = subject(inputSubject, file, AvroSchema.TYPE)

    fun subject(
        inputSubject: String,
        file: String,
        type: String
    ): CompatibilitySubject {
        val compatibilitySubject = CompatibilitySubject(inputSubject, file, type.toSchemaType())
        subjects.add(compatibilitySubject)
        return compatibilitySubject
    }
}

data class CompatibilitySubject(
    val inputSubject: String,
    val file: String,
    val type: SchemaType,
    val references: MutableList<SchemaReference> = mutableListOf(),
    val localReferences: MutableList<LocalReference> = mutableListOf()
) {
    fun addReference(name: String, subject: String, version: Int): CompatibilitySubject {
        if (localReferences.isNotEmpty()) throw MixedReferenceException() // TODO: We can probably remove this
        references.add(SchemaReference(name, subject, version))
        return this
    }

    fun addLocalReference(name: String, path: String): CompatibilitySubject {
        if (references.isNotEmpty()) throw MixedReferenceException() // TODO: We can probably remove this
        localReferences.add(LocalReference(name, path))
        return this
    }
}
