package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.tasks.compatibility.CompatibilitySubjectExtension
import com.github.imflog.schema.registry.tasks.compatibility.CompatibilityTask
import com.github.imflog.schema.registry.tasks.config.ConfigSubjectExtension
import com.github.imflog.schema.registry.tasks.config.ConfigTask
import com.github.imflog.schema.registry.tasks.download.DownloadSubjectExtension
import com.github.imflog.schema.registry.tasks.download.DownloadTask
import com.github.imflog.schema.registry.tasks.register.RegisterSchemasTask
import com.github.imflog.schema.registry.tasks.register.RegisterSubjectExtension
import com.github.imflog.schema.registry.security.BasicAuthExtension
import com.github.imflog.schema.registry.security.SslExtension
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
            val sslExtension = extensions.create(
                "ssl",
                SslExtension::class.java
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
                    it.ssl.set(sslExtension.configs)
                    it.subjects.set(downloadExtension.subjects)
                }

            tasks.register(RegisterSchemasTask.TASK_NAME, RegisterSchemasTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.basicAuth.set(authExtension.basicAuth)
                    it.ssl.set(sslExtension.configs)
                    it.subjects.set(registerExtension.subjects)
                }

            tasks.register(CompatibilityTask.TASK_NAME, CompatibilityTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.basicAuth.set(authExtension.basicAuth)
                    it.ssl.set(sslExtension.configs)
                    it.subjects.set(compatibilityExtension.subjects)
                }

            tasks.register(ConfigTask.TASK_NAME, ConfigTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.basicAuth.set(authExtension.basicAuth)
                    it.ssl.set(sslExtension.configs)
                    it.subjects.set(configExtension.subjects)
                }
        }
    }
}
