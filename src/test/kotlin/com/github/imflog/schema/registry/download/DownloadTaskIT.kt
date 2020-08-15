package com.github.imflog.schema.registry.download

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.imflog.schema.registry.REGISTRY_FAKE_AUTH_PORT
import com.github.imflog.schema.registry.TestContainersUtils
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class DownloadTaskIT : TestContainersUtils() {

    private lateinit var folderRule: TemporaryFolder
    private val subject = "test-subject"

    private val username: String = "user"

    private val password: String = "pass"

    val schema =
        "{\"type\": \"record\", \"name\": \"Blah\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}"

    private val schemaOld =
        "{\"type\": \"record\", \"name\": \"Blah\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }, { \"name\": \"description\", \"type\": \"string\" }]}"

    private lateinit var buildFile: File

    private val mapper = ObjectMapper()

    companion object {
        lateinit var wiremockAuthServerItem: WireMockServer

        @BeforeAll
        @JvmStatic
        fun initClass() {
            wiremockAuthServerItem = WireMockServer(
                WireMockConfiguration
                    .wireMockConfig()
                    .port(REGISTRY_FAKE_AUTH_PORT)
                    .notifier(ConsoleNotifier(true))
            )
            wiremockAuthServerItem.start()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            wiremockAuthServerItem.stop()
        }
    }

    @BeforeEach
    fun init() {
        folderRule = TemporaryFolder()
        folderRule.create()

        // Register schema
        client.register(subject, AvroSchema(schemaOld))
        client.register(subject, AvroSchema(schema))

        // Stub with authentication configuration
        wiremockAuthServerItem.stubFor(
            WireMock.get(
                WireMock
                    .urlMatching("/subjects/test-subject/versions/latest")
            )
                .withBasicAuth(username, password)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Accept", "application/json")
                        .withBody(mapper.writeValueAsString(Schema(subject, 1, 1, "AVRO", emptyList(), schema)))
                )
        )
    }

    @AfterEach
    internal fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `DownloadSchemaTask should download last schema version`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_AUTH_PORT/'
                credentials {
                    username = '$username'
                    password = '$password'
                }
                download {
                    subject('test-subject', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        Assertions.assertThat(File(folderRule.root, "src/main/avro/test")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test/test-subject.avsc")).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `DownloadSchemaTask should download last schema version without credentials`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subject('test-subject', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        Assertions.assertThat(File(folderRule.root, "src/main/avro/test")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test/test-subject.avsc")).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `DownloadSchemaTask should fail download when schema does not exist`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subject('UNKNOWN', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    fun `DownloadSchemaTask should fail download when credentials not setup and required`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_AUTH_PORT/'
                download {
                    subject('test-subject', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    internal fun `DownloadSchemaTask should download specific schema version without credentials`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subject('test-subject', 'src/main/avro/test', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        Assertions.assertThat(File(folderRule.root, "src/main/avro/test")).exists()
        val resultFile = File(folderRule.root, "src/main/avro/test/test-subject.avsc")
        Assertions.assertThat(resultFile).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        Assertions.assertThat(resultFile.readText()).contains("desc")
    }
}
