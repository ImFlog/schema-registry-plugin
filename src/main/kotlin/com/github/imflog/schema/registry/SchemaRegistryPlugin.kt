package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.compatibility.CompatibilitySubjectExtension
import com.github.imflog.schema.registry.compatibility.CompatibilityTask
import com.github.imflog.schema.registry.config.ConfigSubjectExtension
import com.github.imflog.schema.registry.config.ConfigTask
import com.github.imflog.schema.registry.download.DownloadSubjectExtension
import com.github.imflog.schema.registry.download.DownloadTask
import com.github.imflog.schema.registry.register.RegisterSchemasTask
import com.github.imflog.schema.registry.register.RegisterSubjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class SchemaRegistryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val globalExtension = extensions.create(
                "schemaRegistry",
                SchemaRegistryExtension::class.java
            )
            val authExtension = extensions.create(
                "credentials",
                BasicAuthExtension::class.java
            )
            val downloadExtension = extensions.create(
                "download",
                DownloadSubjectExtension::class.java
            )
            val registerExtension = extensions.create(
                "register",
                RegisterSubjectExtension::class.java
            )
            val compatibilityExtension = extensions.create(
                "compatibility",
                CompatibilitySubjectExtension::class.java
            )
            val configExtension = extensions.create(
                "config",
                ConfigSubjectExtension::class.java
            )

            tasks.register(DownloadTask.TASK_NAME, DownloadTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.basicAuth.set(authExtension.basicAuth)
                    it.subjects.set(downloadExtension.subjects)
                }

            tasks.register(RegisterSchemasTask.TASK_NAME, RegisterSchemasTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.basicAuth.set(authExtension.basicAuth)
                    it.subjects.set(registerExtension.subjects)
                }

            tasks.register(CompatibilityTask.TASK_NAME, CompatibilityTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.basicAuth.set(authExtension.basicAuth)
                    it.subjects.set(compatibilityExtension.subjects)
                }

            tasks.register(ConfigTask.TASK_NAME, ConfigTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.basicAuth.set(authExtension.basicAuth)
                    it.subjects.set(configExtension.subjects)
                }
        }
    }
}
