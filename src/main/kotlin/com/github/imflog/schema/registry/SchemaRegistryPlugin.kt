package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.compatibility.CompatibilitySubjectExtension
import com.github.imflog.schema.registry.compatibility.CompatibilityTask
import com.github.imflog.schema.registry.compatibility.TEST_SCHEMAS_TASK
import com.github.imflog.schema.registry.download.DOWNLOAD_SCHEMAS_TASK
import com.github.imflog.schema.registry.download.DownloadTask
import com.github.imflog.schema.registry.register.REGISTER_SCHEMAS_TASK
import com.github.imflog.schema.registry.register.RegisterSchemasTask
import com.github.imflog.schema.registry.register.RegisterSubjectExtension
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
                    SubjectExtension::class.java)
            val registerExtension = extensions.create(
                    "register",
                    RegisterSubjectExtension::class.java)
            val compatibilityExtension = extensions.create(
                    "compatibility",
                    CompatibilitySubjectExtension::class.java)
            val authExtension = extensions.create(
                    "credentials",
                    SchemaRegistryBasicAuthExtension::class.java
            )

            afterEvaluate {
                tasks.create(
                        DOWNLOAD_SCHEMAS_TASK, DownloadTask::class.java).apply {
                    url = globalExtension.url
                    basicAuth = authExtension
                    subjects = downloadExtension.subjects
                }

                tasks.create(
                        REGISTER_SCHEMAS_TASK, RegisterSchemasTask::class.java).apply {
                    url = globalExtension.url
                    auth = authExtension
                    subjects = registerExtension.subjects
                }

                tasks.create(
                        TEST_SCHEMAS_TASK, CompatibilityTask::class.java).apply {
                    url = globalExtension.url
                    auth = authExtension
                    subjects = compatibilityExtension.subjects
                }
            }
        }
    }
}
