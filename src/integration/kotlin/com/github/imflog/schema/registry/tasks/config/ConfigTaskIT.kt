package com.github.imflog.schema.registry.tasks.config

import com.github.imflog.schema.registry.utils.GradleVersions
import com.github.imflog.schema.registry.utils.KafkaTestContainersUtils
import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.UUID

@ParameterizedClass
@ValueSource(strings = [GradleVersions.MINIMUM, GradleVersions.CURRENT])
class ConfigTaskIT(private val gradleVersion: String) : KafkaTestContainersUtils() {
    @TempDir
    lateinit var tempDir: File
    private lateinit var buildFile: File
    private lateinit var subjectId: String

    @BeforeEach
    fun init() {
        subjectId = UUID.randomUUID().toString().take(8)
    }

    @Test
    fun `ConfigTask should set subject compatibility`() {
        buildFile = tempDir.resolve("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                config {
                    subject('testSubject1-$subjectId', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(tempDir)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `ConfigTask should be UP-TO-DATE on second run`() {
        buildFile = tempDir.resolve("build.gradle")
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

        // When
        val result1: BuildResult = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(tempDir)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        Assertions.assertThat(result1.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // When (second run)
        val result2: BuildResult = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(tempDir)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        Assertions.assertThat(result2.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `ConfigTask should detect and reject invalid compatibility settings`() {
        buildFile = tempDir.resolve("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                config {
                    subject('testSubject1-$subjectId', 'FULL_TRANSITIVE')
                    subject('testSubject2-$subjectId', 'FUL_TRANSITIVE') // intentionally broken
                }
            }
            """.trimIndent()
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(tempDir)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }
}
