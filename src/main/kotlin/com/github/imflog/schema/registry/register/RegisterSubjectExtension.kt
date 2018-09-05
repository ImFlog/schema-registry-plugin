package com.github.imflog.schema.registry.register

open class RegisterSubjectExtension {
    val subjects: ArrayList<Triple<String, String, List<String>>> = ArrayList()

    fun subject(inputSubject: String, file: String) {
        subjects.add(Triple(inputSubject, file, emptyList()))
    }

    fun subject(inputSubject: String, file: String, dependencies: List<String>) {
        subjects.add(Triple(inputSubject, file, dependencies))
    }
}