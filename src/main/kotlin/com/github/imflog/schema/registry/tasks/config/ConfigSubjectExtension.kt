package com.github.imflog.schema.registry.tasks.config

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import java.io.Serializable

open class ConfigSubjectExtension(objects: ObjectFactory) {

    companion object {
        const val EXTENSION_NAME = "config"
    }

    val subjects: ListProperty<ConfigSubject> = objects.listProperty(ConfigSubject::class.java)

    fun subject(inputSubject: String, compatibility: String) {
        subjects.add(ConfigSubject(inputSubject, compatibility))
    }
}

data class ConfigSubject(
    val inputSubject: String,
    val compatibility: String
) : Serializable
