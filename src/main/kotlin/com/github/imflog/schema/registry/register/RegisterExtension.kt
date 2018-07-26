package com.github.imflog.schema.registry.register

import com.github.imflog.schema.registry.StringToFileSubject
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty

open class RegisterExtension(project: Project) {

    val subjects: ListProperty<StringToFileSubject> = project.objects.listProperty(StringToFileSubject::class.java)

    fun subject(inputSubject: String, file: String) {
        subjects.add(StringToFileSubject(inputSubject, file))
    }
}

