package com.github.imflog.schema.registry.register

import com.github.imflog.schema.registry.RegistryClientWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

const val REGISTER_SCHEMAS_TASK = "registerSchemasTask"

open class RegisterSchemasTask : DefaultTask() {
    init {
        group = "registry"
        description = "Register schemas in the registry"
    }

    @Input
    lateinit var url: String

    @Input
    lateinit var subjects: ArrayList<Pair<String, List<String>>>

    @TaskAction
    fun registerSchemas() {
        val errorCount = RegisterTaskAction(
                RegistryClientWrapper.client(url)!!,
                subjects,
                project.rootDir
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not registered, see logs for details", Throwable())
        }
    }


}