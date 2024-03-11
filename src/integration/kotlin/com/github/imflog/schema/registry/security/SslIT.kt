package com.github.imflog.schema.registry.security

import com.github.imflog.schema.registry.tasks.config.ConfigTask
import com.github.imflog.schema.registry.utils.KafkaTestContainersUtils
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class SslIT : KafkaTestContainersUtils() {

    private val folderRule: TemporaryFolder = TemporaryFolder()
    private lateinit var buildFile: File

    @BeforeEach
    fun init() {
        folderRule.create()

        val keystoreFile = folderRule.newFile("registry.keystore.jks")
        keystoreFile.writeBytes(
            SslIT::class.java
                .getResource("/secrets/registry.keystore.jks")
                .readBytes()
        )
        val truststore = folderRule.newFile("registry.truststore.jks")
        truststore.writeBytes(
            SslIT::class.java
                .getResource("/secrets/registry.truststore.jks")
                .readBytes()
        )
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `Should fail with incorrect ssl property`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }
            schemaRegistry {
                url = '$schemaRegistrySslEndpoint'
                ssl {
                    configs = [
                        "ssl.truststore.location": "${folderRule.root.absolutePath}/registry.truststore.jks",
                        "ssl.truststore.password": "registry",
                        "ssl.keystore.location": "${folderRule.root.absolutePath}/registry.keystore.jks",
                        "ssl.keystore.password": "registry",
                        "foo": "bar"
                    ]
                }
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
            .withProjectDir(folderRule.root)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions
            .assertThat(result?.task(":configSubjectsTask")?.outcome)
            .isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    fun `Should use SSL correctly`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }
            schemaRegistry {
                url = '$schemaRegistrySslEndpoint'
                ssl {
                    configs = [
                        "ssl.truststore.location": "${folderRule.root.absolutePath}/registry.truststore.jks",
                        "ssl.truststore.password": "registry",
                        "ssl.keystore.location": "${folderRule.root.absolutePath}/registry.keystore.jks",
                        "ssl.keystore.password": "registry"
                    ]
                }
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
            .withProjectDir(folderRule.root)
            .withArguments(ConfigTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions
            .assertThat(result?.task(":configSubjectsTask")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
    }
}
