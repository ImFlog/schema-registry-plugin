package com.github.imflog.gradle.schema

import org.gradle.api.Plugin
import org.gradle.api.Project

class SchemaRegistryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val extension = extensions.create(
                    "schemaRegistry",
                    SchemaRegistryExtension::class.java,
                    project)

            val downloadTask = tasks.create(
                    "downloadSchemaTask", DownloadSchemasTask::class.java).apply {
                outputPath = extension.output
                url = extension.url
                subjects = extension.subjects
            }

            // Add the dependency
            tasks.all {
                if (it.name == "compileJava" || it.name == "compileKotlin") {
                    it.dependsOn(downloadTask)
                }
            }
        }
    }
}