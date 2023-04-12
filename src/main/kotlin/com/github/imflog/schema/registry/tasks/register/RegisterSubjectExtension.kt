package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.Subject
import com.github.imflog.schema.registry.toSchemaType
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

open class RegisterSubjectExtension(objects: ObjectFactory) {
    companion object {
        const val EXTENSION_NAME = "register"
    }

    val subjects: ListProperty<Subject> = objects.listProperty(Subject::class.java)

    fun subject(inputSubject: String, file: String) = subject(inputSubject, file, AvroSchema.TYPE)

    fun subject(
        inputSubject: String,
        file: String,
        type: String
    ): Subject {
        val subject = Subject(inputSubject, file, type)
        subjects.add(subject)
        return subject
    }

    fun subject(subject: Subject) = subject.apply { subjects.add(subject) }
}
