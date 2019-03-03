package com.github.imflog.schema.registry.compatibility

import com.github.imflog.schema.registry.RegistryClientWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

const val TEST_SCHEMAS_TASK = "testSchemasTask"

open class CompatibilityTask : DefaultTask() {
    init {
        group = "registry"
        description = "Test compatibility against registry"
    }

    @Input
    lateinit var url: String

    @Input
    lateinit var userInfo: String //username:password

    @Input
    lateinit var subjects: List<Triple<String, String, List<String>>>

    @TaskAction
    fun testCompatibility() {
        val errorCount = CompatibilityTaskAction(
                RegistryClientWrapper.client(url, userInfo)!!,
                subjects,
                project.rootDir
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not compatible, see logs for details.", Throwable())
        }
    }
}