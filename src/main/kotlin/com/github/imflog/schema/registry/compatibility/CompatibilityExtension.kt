package com.github.imflog.schema.registry.compatibility

open class CompatibilityExtension {
    val subjects: ArrayList<Pair<String, String>> = ArrayList()

    fun subject(inputSubject: String, file: String) {
        subjects + Pair(inputSubject, file)
    }
}