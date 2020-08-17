package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.download.DownloadTask
import org.assertj.core.api.Assertions
import org.gradle.api.Project
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class SchemaRegistryPluginTest {
    lateinit var project: Project
    lateinit var folderRule: TemporaryFolder
    lateinit var buildFile: File

    private val subject = "test-subject"

    @BeforeEach
    fun init() {
        folderRule = TemporaryFolder()
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(SchemaRegistryPlugin::class.java)
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
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
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
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
                .withGradleVersion("6.2.2")
                .withProjectDir(folderRule.root)
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
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
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
                .withGradleVersion("6.2.2")
                .withProjectDir(folderRule.root)
                .withArguments(DownloadTask.TASK_NAME)
                .withPluginClasspath()
                .withDebug(true)
                .build()
            Assertions.fail<Any>("Should not reach this point")
        } catch (ex: UnexpectedBuildFailure) {
            Assertions.assertThat(ex.message).containsIgnoringCase("unknown property 'credentialsBar'")
        }
    }
}
