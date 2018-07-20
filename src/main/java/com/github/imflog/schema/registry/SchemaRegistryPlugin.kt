package com.github.imflog.schema.registry

import org.gradle.api.Plugin
import org.gradle.api.Project

class SchemaRegistryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val extension = extensions.create(
                    "schemaRegistry",
                    SchemaRegistryExtension::class.java,
                    project)

            tasks.create(
                    DOWNLOAD_SCHEMA_TASK, DownloadSchemasTask::class.java).apply {
                outputPath = extension.output
                url = extension.url
                subjects = extension.subjects
            }
        }
    }
}