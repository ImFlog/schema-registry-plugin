package com.github.imflog.schema.registry.tasks.config

import com.github.imflog.schema.registry.TestContainersUtils
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ConfigTaskIT : TestContainersUtils() {
    private lateinit var folderRule: TemporaryFolder
    private lateinit var buildFile: File

    @BeforeEach
    fun init() {
        folderRule = TemporaryFolder()
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `ConfigTask should set subject compatibility`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `ConfigTask should detect and reject invalid compatibility settings`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                    subject('testSubject2', 'FUL_TRANSITIVE') // intentionally broken
                }
            }
            """.trimIndent()
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }
}
