package com.github.imflog.schema.registry.register

import com.github.imflog.schema.registry.download.Subject
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty

open class RegisterSchemasExtension(project: Project) {

    val subjects: ListProperty<Subject> = project.objects.listProperty(Subject::class.java)

    fun subject(inputSubject: String, file: String) {
        subjects.add(Subject(inputSubject, file))
    }
}

