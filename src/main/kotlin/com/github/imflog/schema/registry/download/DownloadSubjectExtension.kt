package com.github.imflog.schema.registry.download

open class DownloadSubjectExtension {
    val subjects: MutableList<DownloadSubject> = ArrayList()

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
