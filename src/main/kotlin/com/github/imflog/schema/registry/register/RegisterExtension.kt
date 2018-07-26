package com.github.imflog.schema.registry.register

import com.github.imflog.schema.registry.StringFileSubject
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty

open class RegisterExtension {

    val subjects: ArrayList<StringFileSubject> = ArrayList()

    fun subject(inputSubject: String, file: String) {
        subjects.add(StringFileSubject(inputSubject, file))
    }
}

