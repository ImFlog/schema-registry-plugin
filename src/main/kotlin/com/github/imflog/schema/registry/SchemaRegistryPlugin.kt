package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.compatibility.CompatibilityExtension
import com.github.imflog.schema.registry.compatibility.CompatibilityTask
import com.github.imflog.schema.registry.compatibility.TEST_SCHEMAS_TASK
import com.github.imflog.schema.registry.download.DOWNLOAD_SCHEMAS_TASK
import com.github.imflog.schema.registry.download.DownloadExtension
import com.github.imflog.schema.registry.download.DownloadTask
import com.github.imflog.schema.registry.register.REGISTER_SCHEMAS_TASK
import com.github.imflog.schema.registry.register.RegisterExtension
import com.github.imflog.schema.registry.register.RegisterSchemasTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class SchemaRegistryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val globalExtension = extensions.create(
                    "schemaRegistry",
                    SchemaRegistryExtension::class.java)
            val downloadExtension = extensions.create(
                    "download",
                    DownloadExtension::class.java)
            val registerExtension = extensions.create(
                    "register",
                    RegisterExtension::class.java)
            val compatibilityExtension = extensions.create(
                    "compatibility",
                    CompatibilityExtension::class.java)

            afterEvaluate {
                tasks.create(
                        DOWNLOAD_SCHEMAS_TASK, DownloadTask::class.java).apply {
                    outputPath = downloadExtension.output
                    url = globalExtension.url
                    subjects = downloadExtension.subjects
                }

                tasks.create(
                        REGISTER_SCHEMAS_TASK, RegisterSchemasTask::class.java).apply {
                    url = globalExtension.url
                    subjects = registerExtension.subjects
                }

                tasks.create(
                        TEST_SCHEMAS_TASK, CompatibilityTask::class.java).apply {
                    url = globalExtension.url
                    subjects = compatibilityExtension.subjects
                }
            }
        }
    }
}