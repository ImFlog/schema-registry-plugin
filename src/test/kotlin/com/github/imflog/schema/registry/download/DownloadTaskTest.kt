package com.github.imflog.schema.registry.download

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.imflog.schema.registry.REGISTRY_FAKE_AUTH_PORT
import com.github.imflog.schema.registry.REGISTRY_FAKE_PORT
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import org.assertj.core.api.Assertions.assertThat
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

class DownloadTaskTest {

    lateinit var folderRule: TemporaryFolder
    val subject = "test-subject"

    val username: String = "user"

    val password: String = "pass"

    val schema =
        "{\"type\": \"record\", \"name\": \"Blah\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}"

    lateinit var buildFile: File

    val mapper = ObjectMapper()

    companion object {
        lateinit var wiremockServerItem: WireMockServer
        lateinit var wiremockAuthServerItem: WireMockServer

        @BeforeAll
        @JvmStatic
        fun initClass() {
            wiremockServerItem = WireMockServer(
                WireMockConfiguration
                    .wireMockConfig()
                    .port(REGISTRY_FAKE_PORT)
                    .notifier(ConsoleNotifier(true))
            )
            wiremockServerItem.start()

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
            wiremockServerItem.stop()
            wiremockAuthServerItem.stop()
        }
    }

    @BeforeEach
    fun init() {
        folderRule = TemporaryFolder()
        folderRule.create()

        // Register schema
        val avroSchema = Schema(subject, 1, 1, schema)
        // Stub without authentication configuration
        wiremockServerItem.stubFor(
            WireMock.get(
                WireMock
                    .urlMatching("/subjects/test-subject/versions/latest")
            )
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Accept", "application/json")
                        .withBody(mapper.writeValueAsString(avroSchema))
                )
        )
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
                        .withBody(mapper.writeValueAsString(avroSchema))
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
            .withGradleVersion("5.6.4")
            .withProjectDir(folderRule.root)
            .withArguments(DOWNLOAD_SCHEMAS_TASK)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        assertThat(File(folderRule.root, "src/main/avro/test")).exists()
        assertThat(File(folderRule.root, "src/main/avro/test/test-subject.avsc")).exists()
        assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
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
                url = 'http://localhost:$REGISTRY_FAKE_PORT/'
                download {
                    subject('test-subject', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("5.6.4")
            .withProjectDir(folderRule.root)
            .withArguments(DOWNLOAD_SCHEMAS_TASK)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        assertThat(File(folderRule.root, "src/main/avro/test")).exists()
        assertThat(File(folderRule.root, "src/main/avro/test/test-subject.avsc")).exists()
        assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
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
                url = 'http://localhost:$REGISTRY_FAKE_PORT/'
                download {
                    subject('UNKNOWN', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("5.6.4")
            .withProjectDir(folderRule.root)
            .withArguments(DOWNLOAD_SCHEMAS_TASK)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
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
            .withGradleVersion("5.6.4")
            .withProjectDir(folderRule.root)
            .withArguments(DOWNLOAD_SCHEMAS_TASK)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }
}
