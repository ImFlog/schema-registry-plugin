package com.github.imflog.schema.registry.register

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

open class RegisterSubjectExtension(objects: ObjectFactory) {
    val subjects: ListProperty<RegisterSubject> = objects.listProperty(RegisterSubject::class.java)

    fun subject(inputSubject: String, file: String) {
        subjects.add(RegisterSubject(inputSubject, file, emptyList()))
    }

    fun subject(inputSubject: String, file: String, dependencies: List<String>) {
        subjects.add(RegisterSubject(inputSubject, file, dependencies))
    }
}

data class RegisterSubject(
    val inputSubject: String,
    val file: String,
    val dependencies: List<String>
)
