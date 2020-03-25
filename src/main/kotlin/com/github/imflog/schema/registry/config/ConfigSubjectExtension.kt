package com.github.imflog.schema.registry.config

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

open class ConfigSubjectExtension(objects: ObjectFactory) {
    val subjects: ListProperty<ConfigSubject> = objects.listProperty(ConfigSubject::class.java)

    fun subject(inputSubject: String, compatibility: String) {
        subjects.add(ConfigSubject(inputSubject, compatibility))
    }
}

data class ConfigSubject(
    val inputSubject: String,
    val compatibility: String
)
