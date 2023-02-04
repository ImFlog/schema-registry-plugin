package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.Subject
import com.github.imflog.schema.registry.toSchemaType
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

open class CompatibilitySubjectExtension(objects: ObjectFactory) {
    companion object {
        const val EXTENSION_NAME = "compatibility"
    }

    val subjects: ListProperty<Subject> = objects.listProperty(Subject::class.java)

    fun subject(inputSubject: String, file: String) = subject(inputSubject, file, AvroSchema.TYPE)

    fun subject(
        inputSubject: String,
        file: String,
        type: String
    ): Subject {
        val compatibilitySubject = Subject(inputSubject, file, type.toSchemaType())
        subjects.add(compatibilitySubject)
        return compatibilitySubject
    }

    fun subject(subject: Subject) = subject.apply { subjects.add(subject) }
}
