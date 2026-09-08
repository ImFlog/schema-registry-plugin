package com.github.imflog.schema.registry.security

import com.github.imflog.schema.registry.tasks.config.ConfigTask
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

@ParameterizedClass
@ValueSource(strings = [GradleVersions.MINIMUM, GradleVersions.CURRENT])
class SslIT(private val gradleVersion: String) : KafkaTestContainersUtils() {

    @TempDir
    lateinit var tempDir: File
    private lateinit var buildFile: File

    @BeforeEach
    fun init() {
        val keystoreFile = tempDir.resolve("registry.keystore.jks")
        keystoreFile.writeBytes(
            SslIT::class.java
                .getResource("/secrets/registry.keystore.jks")
                .readBytes()
        )
        val truststore = tempDir.resolve("registry.truststore.jks")
        truststore.writeBytes(
            SslIT::class.java
                .getResource("/secrets/registry.truststore.jks")
                .readBytes()
        )
    }

    @Test
    fun `Should use SSL correctly`() {
        buildFile = tempDir.resolve("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }
            schemaRegistry {
                url = '$schemaRegistrySslEndpoint'
                clientConfig = [
                    "schema.registry.ssl.truststore.location": "${tempDir.absolutePath}/registry.truststore.jks",
                    "schema.registry.ssl.truststore.password": "registry",
                    "schema.registry.ssl.keystore.location": "${tempDir.absolutePath}/registry.keystore.jks",
                    "schema.registry.ssl.keystore.password": "registry"
                ]
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(tempDir)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions
            .assertThat(result?.task(":configSubjectsTask")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }
}
