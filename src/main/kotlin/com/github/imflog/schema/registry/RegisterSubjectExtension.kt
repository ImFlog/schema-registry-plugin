package com.github.imflog.schema.registry

open class RegisterSubjectExtension {
    val subjects: ArrayList<Pair<String, List<String>>> = ArrayList()

    fun subject(inputSubject: String, files: List<String>) {
        subjects.add(Pair(inputSubject, files))
    }
}