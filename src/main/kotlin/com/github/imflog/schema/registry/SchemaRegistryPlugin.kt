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

            globalExtension.quiet.map {
                LoggingUtils.quietLogging = it
            }

            tasks.register(DownloadTask.TASK_NAME, DownloadTask::class.java)
                .configure { downloadTask ->
                    downloadTask.url.set(globalExtension.url)
                    downloadTask.basicAuth.set(basicAuthExtension.basicAuth)
                    downloadTask.ssl.set(sslExtension.configs)
                    downloadTask.subjects.set(downloadExtension.subjects)
                }

            tasks.register(RegisterSchemasTask.TASK_NAME, RegisterSchemasTask::class.java)
                .configure { registerSchemasTask ->
                    registerSchemasTask.url.set(globalExtension.url)
                    registerSchemasTask.basicAuth.set(basicAuthExtension.basicAuth)
                    registerSchemasTask.ssl.set(sslExtension.configs)
                    registerSchemasTask.subjects.set(registerExtension.subjects)
                    registerSchemasTask.outputDirectory.set(globalExtension.outputDirectory)
                }

            tasks.register(CompatibilityTask.TASK_NAME, CompatibilityTask::class.java)
                .configure { compatibilityTask ->
                    compatibilityTask.url.set(globalExtension.url)
                    compatibilityTask.basicAuth.set(basicAuthExtension.basicAuth)
                    compatibilityTask.ssl.set(sslExtension.configs)
                    compatibilityTask.subjects.set(compatibilityExtension.subjects)
                }

            tasks.register(ConfigTask.TASK_NAME, ConfigTask::class.java)
                .configure { configTask ->
                    configTask.url.set(globalExtension.url)
                    configTask.basicAuth.set(basicAuthExtension.basicAuth)
                    configTask.ssl.set(sslExtension.configs)
                    configTask.subjects.set(configExtension.subjects)
                }
        }
    }
}
