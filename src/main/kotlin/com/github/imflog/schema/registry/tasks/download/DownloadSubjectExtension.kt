package com.github.imflog.schema.registry.tasks.download

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

open class DownloadSubjectExtension(objects: ObjectFactory) {
    val subjects: ListProperty<DownloadSubject> = objects.listProperty(DownloadSubject::class.java).apply {
        convention(listOf())
    }

    fun subject(inputSubject: String, file: String, version: Int) {
        subjects.add(DownloadSubject(inputSubject, file, version))
    }

    fun subject(inputSubject: String, file: String) {
        subjects.add(DownloadSubject(inputSubject, file))
    }
}

data class DownloadSubject(
    val subject: String,
    val file: String,
    val version: Int? = null
)
