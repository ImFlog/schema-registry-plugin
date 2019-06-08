package com.github.imflog.schema.registry.config

open class ConfigSubjectExtension {
    val subjects: ArrayList<Pair<String, String>> = ArrayList()

    fun subject(inputSubject: String, compatibility: String) {
        subjects.add(Pair(inputSubject, compatibility))
    }
}