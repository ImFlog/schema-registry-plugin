package com.github.imflog.schema.registry.compatibility

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty


open class CompatibilitySubjectExtension(objects: ObjectFactory) {
    val subjects: ListProperty<CompatibilitySubject> = objects.listProperty(CompatibilitySubject::class.java)

    fun subject(inputSubject: String, file: String) {
        subjects.add(CompatibilitySubject(inputSubject, file, emptyList()))
    }

    fun subject(inputSubject: String, file: String, dependencies: List<String>) {
        subjects.add(CompatibilitySubject(inputSubject, file, dependencies))
    }
}

data class CompatibilitySubject(
    val inputSubject: String,
    val file: String,
    val dependencies: List<String>
)
