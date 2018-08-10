package com.github.imflog.schema.registry

open class SubjectExtension {
    val subjects: ArrayList<Pair<String, String>> = ArrayList()

    fun subject(inputSubject: String, file: String) {
        subjects.add(Pair(inputSubject, file))
    }
}