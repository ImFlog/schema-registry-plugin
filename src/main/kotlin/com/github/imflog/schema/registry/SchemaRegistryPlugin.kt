package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.download.DOWNLOAD_SCHEMAS_TASK
import com.github.imflog.schema.registry.download.DownloadSchemasExtension
import com.github.imflog.schema.registry.download.DownloadSchemasTask
import com.github.imflog.schema.registry.register.REGISTER_SCHEMAS_TASK
import com.github.imflog.schema.registry.register.RegisterSchemasExtension
import com.github.imflog.schema.registry.register.RegisterSchemasTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class SchemaRegistryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val globalExtension = extensions.create(
                    "schemaRegistry",
                    SchemaRegistryExtension::class.java,
                    project)
            val downloadExtension = extensions.create(
                    "download",
                    DownloadSchemasExtension::class.java,
                    project)
            val registerExtension = extensions.create(
                    "register",
                    RegisterSchemasExtension::class.java,
                    project)

            tasks.create(
                    DOWNLOAD_SCHEMAS_TASK, DownloadSchemasTask::class.java).apply {
                outputPath = downloadExtension.output
                url = globalExtension.url
                subjects = downloadExtension.subjects
            }

            tasks.create(
                    REGISTER_SCHEMAS_TASK, RegisterSchemasTask::class.java).apply {
                url = globalExtension.url
                subjects = registerExtension.subjects
            }
        }
    }
}