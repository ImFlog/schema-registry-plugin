package com.github.imflog.schema.registry.compatibility

import com.github.imflog.schema.registry.RegistryClientWrapper
import com.github.imflog.schema.registry.StringFileSubject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

const val TEST_SCHEMAS_TASK = "testSchemasTask"

open class CompatibilityTask : DefaultTask() {

    @Input
    lateinit var url: String
    @Input
    lateinit var subjects: ArrayList<StringFileSubject>

    @TaskAction
    fun testCompatibility() {
        val errorCount = CompatibilityTaskAction(
                RegistryClientWrapper.client(url)!!,
                subjects,
                project.rootDir
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not compatible, see logs for details.", Throwable())
        }
    }
}