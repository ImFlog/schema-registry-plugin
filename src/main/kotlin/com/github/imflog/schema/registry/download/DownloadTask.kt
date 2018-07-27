package com.github.imflog.schema.registry.download

import com.github.imflog.schema.registry.RegistryClientWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import java.io.File
import java.io.IOException

const val DOWNLOAD_SCHEMAS_TASK = "downloadSchemasTask"

open class DownloadTask : DefaultTask() {
    init {
        group = "registry"
        description = "Download schemas from the registry"
    }

    @OutputDirectory
    lateinit var outputDir: File

    @Input
    var outputPath: String = ""
        set(path) {
            field = path
            this.outputDir = File(project.rootDir, path)
        }

    @Input
    lateinit var subjects: ArrayList<String>
    @Input
    lateinit var url: String

    @TaskAction
    fun downloadSchemas() {
        val errorCount = DownloadTaskAction(
                RegistryClientWrapper.client(url)!!,
                subjects,
                outputDir)
                .run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not downloaded, see logs for details", Throwable())
        }
    }
}