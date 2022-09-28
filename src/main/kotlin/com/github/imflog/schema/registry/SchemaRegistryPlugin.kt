package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.security.BasicAuthExtension
import com.github.imflog.schema.registry.security.SslExtension
import com.github.imflog.schema.registry.tasks.compatibility.CompatibilitySubjectExtension
import com.github.imflog.schema.registry.tasks.compatibility.CompatibilityTask
import com.github.imflog.schema.registry.tasks.config.ConfigSubjectExtension
import com.github.imflog.schema.registry.tasks.config.ConfigTask
import com.github.imflog.schema.registry.tasks.download.DownloadSubjectExtension
import com.github.imflog.schema.registry.tasks.download.DownloadTask
import com.github.imflog.schema.registry.tasks.register.RegisterSchemasTask
import com.github.imflog.schema.registry.tasks.register.RegisterSubjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

class SchemaRegistryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val globalExtension = extensions.create(
                SchemaRegistryExtension.EXTENSION_NAME,
                SchemaRegistryExtension::class.java
            )

            val basicAuthExtension = (globalExtension as ExtensionAware).extensions.create(
                BasicAuthExtension.EXTENSION_NAME,
                BasicAuthExtension::class.java
            )
            val sslExtension = (globalExtension as ExtensionAware).extensions.create(
                SslExtension.EXTENSION_NAME,
                SslExtension::class.java
            )
            val downloadExtension = (globalExtension as ExtensionAware).extensions.create(
                DownloadSubjectExtension.EXTENSION_NAME,
                DownloadSubjectExtension::class.java
            )
            val registerExtension = (globalExtension as ExtensionAware).extensions.create(
                RegisterSubjectExtension.EXTENSION_NAME,
                RegisterSubjectExtension::class.java
            )
            val compatibilityExtension = (globalExtension as ExtensionAware).extensions.create(
                CompatibilitySubjectExtension.EXTENSION_NAME,
                CompatibilitySubjectExtension::class.java
            )
            val configExtension = (globalExtension as ExtensionAware).extensions.create(
                ConfigSubjectExtension.EXTENSION_NAME,
                ConfigSubjectExtension::class.java
            )

            tasks.register(DownloadTask.TASK_NAME, DownloadTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.quietLogging.set(globalExtension.quiet)
                    it.basicAuth.set(basicAuthExtension.basicAuth)
                    it.ssl.set(sslExtension.configs)
                    it.subjects.set(downloadExtension.subjects)
                }

            tasks.register(RegisterSchemasTask.TASK_NAME, RegisterSchemasTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.quietLogging.set(globalExtension.quiet)
                    it.basicAuth.set(basicAuthExtension.basicAuth)
                    it.ssl.set(sslExtension.configs)
                    it.subjects.set(registerExtension.subjects)
                    it.outputDirectory.set(globalExtension.outputDirectory)
                }

            tasks.register(CompatibilityTask.TASK_NAME, CompatibilityTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.quietLogging.set(globalExtension.quiet)
                    it.basicAuth.set(basicAuthExtension.basicAuth)
                    it.ssl.set(sslExtension.configs)
                    it.subjects.set(compatibilityExtension.subjects)
                }

            tasks.register(ConfigTask.TASK_NAME, ConfigTask::class.java)
                .configure {
                    it.url.set(globalExtension.url)
                    it.basicAuth.set(basicAuthExtension.basicAuth)
                    it.ssl.set(sslExtension.configs)
                    it.subjects.set(configExtension.subjects)
                }
        }
    }
}
