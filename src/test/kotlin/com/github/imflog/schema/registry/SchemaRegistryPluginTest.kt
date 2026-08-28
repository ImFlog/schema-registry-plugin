package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.tasks.compatibility.CompatibilityTask
import com.github.imflog.schema.registry.tasks.config.ConfigTask
import com.github.imflog.schema.registry.tasks.download.DownloadTask
import com.github.imflog.schema.registry.tasks.register.RegisterSchemasTask
import org.assertj.core.api.Assertions
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SchemaRegistryPluginTest {
    lateinit var project: Project
    @TempDir
    lateinit var folderRule: Path
    lateinit var buildFile: File

    private val subject = "test-subject"

    @BeforeEach
    fun init() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(SchemaRegistryPlugin::class.java)
        Files.createFile(folderRule.resolve("build.gradle"))
    }

    @Test
    fun `plugin should add tasks when applied`() {
        project.afterEvaluate {
            val downloadSchemaTask = project.tasks.getByName(DownloadTask.TASK_NAME)
            Assertions.assertThat(downloadSchemaTask).isNotNull()
        }
    }

    @Test
    fun `plugin should fail with wrong url extension configuration`() {
        buildFile = File(folderRule.toFile(), "build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                urlFoo = 'http://localhost:1234/'
                output = 'src/main/avro'
                subjects = ['$subject']
            }
        """
        )

        try {
            GradleRunner.create()
                .withGradleVersion("8.6")
                .withProjectDir(folderRule.toFile())
                .withArguments(DownloadTask.TASK_NAME)
                .withPluginClasspath()
                .withDebug(true)
                .build()
            Assertions.fail<Any>("Should not reach this point")
        } catch (ex: UnexpectedBuildFailure) {
            Assertions.assertThat(ex.message).containsIgnoringCase("unknown property 'urlFoo'")
        }
    }

    @Test
    fun `plugin should only parse nested extensions`() {
        buildFile = File(folderRule.toFile(), "build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            // This should not be taken into account
            credentials {
                username = 'User'
                password = 'Passw0rd'
            }

            schemaRegistry {
                url = 'http://localhost:1234/'
            }
        """
        )

        try {
            GradleRunner.create()
                .withGradleVersion("8.6")
                .withProjectDir(folderRule.toFile())
                .withArguments(DownloadTask.TASK_NAME)
                .withPluginClasspath()
                .withDebug(true)
                .build()
            Assertions.fail<Any>("Should not reach this point")
        } catch (ex: UnexpectedBuildFailure) {
            Assertions.assertThat(ex.message).containsIgnoringCase("Could not find method credentials()")
        }
    }

    @Test
    fun `plugin should propagate the client headers configuration to all the tasks`() {
        val headers = mapOf("X-Api-Key" to "abcd1234", "X-Client-Id" to "my-client-id")
        val extension = project.extensions.getByType(SchemaRegistryExtension::class.java)
        extension.clientHeadersConfig.set(headers)

        Assertions.assertThat(project.headersConfigOf(DownloadTask.TASK_NAME)).isEqualTo(headers)
        Assertions.assertThat(project.headersConfigOf(RegisterSchemasTask.TASK_NAME)).isEqualTo(headers)
        Assertions.assertThat(project.headersConfigOf(CompatibilityTask.TASK_NAME)).isEqualTo(headers)
        Assertions.assertThat(project.headersConfigOf(ConfigTask.TASK_NAME)).isEqualTo(headers)
    }

    @Test
    fun `plugin should default the client headers configuration to an empty map`() {
        val extension = project.extensions.getByType(SchemaRegistryExtension::class.java)

        Assertions.assertThat(extension.clientHeadersConfig.get()).isEmpty()
        Assertions.assertThat(project.headersConfigOf(DownloadTask.TASK_NAME)).isEmpty()
        Assertions.assertThat(project.headersConfigOf(RegisterSchemasTask.TASK_NAME)).isEmpty()
        Assertions.assertThat(project.headersConfigOf(CompatibilityTask.TASK_NAME)).isEmpty()
        Assertions.assertThat(project.headersConfigOf(ConfigTask.TASK_NAME)).isEmpty()
    }

    @Test
    fun `plugin should keep the client config and the client headers config separated`() {
        val extension = project.extensions.getByType(SchemaRegistryExtension::class.java)
        extension.clientConfig.set(mapOf("basic.auth.credentials.source" to "USER_INFO"))
        extension.clientHeadersConfig.set(mapOf("X-Api-Key" to "abcd1234"))

        val downloadTask = project.tasks.getByName(DownloadTask.TASK_NAME) as DownloadTask
        Assertions.assertThat(downloadTask.clientConfig.get())
            .containsExactlyEntriesOf(mapOf("basic.auth.credentials.source" to "USER_INFO"))
        Assertions.assertThat(downloadTask.clientHeadersConfig.get())
            .containsExactlyEntriesOf(mapOf("X-Api-Key" to "abcd1234"))
    }

    private fun Project.headersConfigOf(taskName: String): Map<String, String> =
        when (val task = tasks.getByName(taskName)) {
            is DownloadTask -> task.clientHeadersConfig
            is RegisterSchemasTask -> task.clientHeadersConfig
            is CompatibilityTask -> task.clientHeadersConfig
            is ConfigTask -> task.clientHeadersConfig
            else -> throw IllegalArgumentException("Unknown task $taskName")
        }.get()
}
