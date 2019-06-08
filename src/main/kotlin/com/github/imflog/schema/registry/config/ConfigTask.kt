package com.github.imflog.schema.registry.config

import com.github.imflog.schema.registry.RegistryClientWrapper
import com.github.imflog.schema.registry.SchemaRegistryBasicAuthExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

const val CONFIG_SUBJECTS_TASK = "configSubjectsTask"

open class ConfigTask : DefaultTask() {
    init {
        group = "registry"
        description = "Set subject compatibility in registry"
    }

    @Input
    lateinit var url: String

    @Input
    lateinit var auth: SchemaRegistryBasicAuthExtension

    @Input
    lateinit var subjects: List<Pair<String, String>>

    @TaskAction
    fun configureSubjects() {
        val errorCount = ConfigTaskAction(
            RegistryClientWrapper.client(url, auth),
            subjects
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount subject configuration not set, see logs for details", Throwable())
        }
    }
}