package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.tasks.download.DownloadTask
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
    fun `plugin should fail with wrong credentials extension configuration`() {
        buildFile = File(folderRule.toFile(), "build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:1234/'
                credentials {
                    username = 'user'
                    password = 'pass'
                }
                credentialsBar.username = 'user'
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
            Assertions.assertThat(ex.message).containsIgnoringCase("unknown property 'credentialsBar'")
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
}
