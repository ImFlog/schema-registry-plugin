package com.github.imflog.schema.registry.download

import com.github.imflog.schema.registry.RegistryClientWrapper
import com.github.imflog.schema.registry.SchemaRegistryBasicAuth
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

const val DOWNLOAD_SCHEMAS_TASK = "downloadSchemasTask"

open class DownloadTask : DefaultTask() {
    init {
        group = "registry"
        description = "Download schemas from the registry"
    }

    @Input
    lateinit var subjects: List<Pair<String, String>>

    @Input
    lateinit var auth: SchemaRegistryBasicAuth

    @Input
    lateinit var url: String

    @TaskAction
    fun downloadSchemas() {
        val errorCount = DownloadTaskAction(
                RegistryClientWrapper.client(url,auth)!!,
                subjects,
                project.rootDir)
                .run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not downloaded, see logs for details", Throwable())
        }
    }
}