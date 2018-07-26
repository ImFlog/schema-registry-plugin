package com.github.imflog.schema.registry.compatibility

import com.github.imflog.schema.registry.StringFileSubject

open class CompatibilityExtension {
    val subjects: ArrayList<StringFileSubject> = ArrayList()

    fun subject(inputSubject: String, file: String) {
        subjects.add(StringFileSubject(inputSubject, file))
    }
}