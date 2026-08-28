package com.github.imflog.schema.registry.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.imflog.schema.registry.tasks.compatibility.CompatibilityTask
import com.github.imflog.schema.registry.tasks.config.ConfigTask
import com.github.imflog.schema.registry.tasks.download.DownloadTask
import com.github.imflog.schema.registry.tasks.register.RegisterSchemasTask
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class ClientHeadersIT {

    private lateinit var folderRule: TemporaryFolder
    private lateinit var buildFile: File

    private val avroSchema = """
        {
            "type":"record",
            "name":"Blah",
            "fields":[
                {
                    "name":"name",
                    "type":"string"
                }
            ]
        }
    """.trimIndent()

    companion object {
        private const val REGISTRY_FAKE_HEADERS_PORT = 7778
        private const val API_KEY_HEADER = "X-Api-Key"
        private const val API_KEY_VALUE = "abcd1234"
        private const val CLIENT_ID_HEADER = "X-Client-Id"
        private const val CLIENT_ID_VALUE = "my-client-id"
        private const val USERNAME = "user"
        private const val PASSWORD = "pass"

        private val mapper = ObjectMapper()

        lateinit var wireMockServerItem: WireMockServer

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            wireMockServerItem = WireMockServer(
                WireMockConfiguration.wireMockConfig()
                    .port(REGISTRY_FAKE_HEADERS_PORT)
                    .notifier(ConsoleNotifier(true))
            )
            wireMockServerItem.start()
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            wireMockServerItem.stop()
        }
    }

    /**
     * The headers declared in the schemaRegistry extension, as a groovy map.
     */
    private val headersConfig = """
        clientHeadersConfig = [
            '$API_KEY_HEADER': '$API_KEY_VALUE',
            '$CLIENT_ID_HEADER': '$CLIENT_ID_VALUE'
        ]
    """.trimIndent()

    @BeforeEach
    fun init() {
        folderRule = TemporaryFolder()
        folderRule.create()
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
        wireMockServerItem.resetAll()
    }

    private fun runTask(taskName: String): BuildResult? = GradleRunner.create()
        .withGradleVersion("8.6")
        .withProjectDir(folderRule.root)
        .withArguments(taskName)
        .withPluginClasspath()
        .withDebug(true)
        .build()

    private fun failTask(taskName: String): BuildResult? = GradleRunner.create()
        .withGradleVersion("8.6")
        .withProjectDir(folderRule.root)
        .withArguments(taskName)
        .withPluginClasspath()
        .withDebug(true)
        .buildAndFail()

    @Nested
    inner class ConfigurationTest {

        @BeforeEach
        fun setup() {
            // Stub that only matches when both headers are present
            wireMockServerItem.stubFor(
                WireMock.put(WireMock.urlMatching("/config/.*"))
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody("""{ "compatibility": "FULL_TRANSITIVE" }""")
                    )
            )
        }

        @Test
        fun `ConfigTask should send the configured headers`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                $headersConfig
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = runTask(ConfigTask.TASK_NAME)

            Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            wireMockServerItem.verify(
                WireMock.putRequestedFor(WireMock.urlMatching("/config/.*"))
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
            )
        }

        @Test
        fun `ConfigTask should fail when the required headers are not configured`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = failTask(ConfigTask.TASK_NAME)

            Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    @Nested
    inner class ClientConfigCompatibilityTest {

        @BeforeEach
        fun setup() {
            // Stub that only matches when both the headers and the basic auth credentials are present
            wireMockServerItem.stubFor(
                WireMock.put(WireMock.urlMatching("/config/.*"))
                    .withBasicAuth(USERNAME, PASSWORD)
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody("""{ "compatibility": "FULL_TRANSITIVE" }""")
                    )
            )
        }

        @Test
        fun `ConfigTask should send the headers along with the clientConfig properties`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                clientConfig = [
                    'basic.auth.credentials.source': 'USER_INFO',
                    'basic.auth.user.info': '$USERNAME:$PASSWORD'
                ]
                $headersConfig
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = runTask(ConfigTask.TASK_NAME)

            Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Nested
    inner class NoHeadersTest {

        @BeforeEach
        fun setup() {
            wireMockServerItem.stubFor(
                WireMock.put(WireMock.urlMatching("/config/.*"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody("""{ "compatibility": "FULL_TRANSITIVE" }""")
                    )
            )
        }

        @Test
        fun `ConfigTask should work when no header is configured`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = runTask(ConfigTask.TASK_NAME)

            Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Nested
    inner class DownloadTest {

        private val subject = "test-subject"

        @BeforeEach
        fun setup() {
            wireMockServerItem.stubFor(
                WireMock.get(WireMock.urlMatching("/subjects/$subject/versions/latest"))
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Accept", "application/json")
                            .withBody(
                                mapper.writeValueAsString(
                                    Schema(subject, 1, 1, AvroSchema.TYPE, emptyList(), avroSchema)
                                )
                            )
                    )
            )
        }

        @Test
        fun `DownloadSchemaTask should send the configured headers`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                $headersConfig
                download {
                    subject('$subject', 'src/main/avro/test')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = runTask(DownloadTask.TASK_NAME)

            Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            Assertions.assertThat(File(folderRule.root, "src/main/avro/test/$subject.avsc")).exists()
            wireMockServerItem.verify(
                WireMock.getRequestedFor(WireMock.urlMatching("/subjects/$subject/versions/latest"))
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
            )
        }

        @Test
        fun `DownloadSchemaTask should fail when the required headers are not configured`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                download {
                    subject('$subject', 'src/main/avro/test')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = failTask(DownloadTask.TASK_NAME)

            Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    @Nested
    inner class RegisterTest {

        @BeforeEach
        fun setup() {
            wireMockServerItem.stubFor(
                WireMock.post(WireMock.urlMatching("/subjects/.*/versions\\?normalize=false"))
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody("""{"id": 1}""")
                    )
            )
        }

        @Test
        fun `RegisterSchemasTask should send the configured headers`() {
            folderRule.newFolder("avro")
            folderRule.newFile("avro/test.avsc").writeText(avroSchema)

            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                $headersConfig
                register {
                    subject('testSubject1', 'avro/test.avsc')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = runTask(RegisterSchemasTask.TASK_NAME)

            Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            wireMockServerItem.verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/subjects/.*/versions\\?normalize=false"))
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
            )
        }

        @Test
        fun `RegisterSchemasTask should fail when the required headers are not configured`() {
            folderRule.newFolder("avro")
            folderRule.newFile("avro/test.avsc").writeText(avroSchema)

            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                register {
                    subject('testSubject1', 'avro/test.avsc')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = failTask(RegisterSchemasTask.TASK_NAME)

            Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    @Nested
    inner class CompatibilityTest {

        @BeforeEach
        fun setup() {
            wireMockServerItem.stubFor(
                WireMock.post(WireMock.urlMatching("/compatibility/subjects/.*/versions/.*"))
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/vnd.schemaregistry.v1+json")
                            .withBody("""{"is_compatible": true}""")
                    )
            )
        }

        @Test
        fun `CompatibilityTask should send the configured headers`() {
            folderRule.newFolder("avro")
            folderRule.newFile("avro/test.avsc").writeText(avroSchema)

            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                $headersConfig
                compatibility {
                    subject('testSubject1', 'avro/test.avsc')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = runTask(CompatibilityTask.TASK_NAME)

            Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            wireMockServerItem.verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/compatibility/subjects/.*/versions/.*"))
                    .withHeader(API_KEY_HEADER, WireMock.equalTo(API_KEY_VALUE))
                    .withHeader(CLIENT_ID_HEADER, WireMock.equalTo(CLIENT_ID_VALUE))
            )
        }

        @Test
        fun `CompatibilityTask should fail when the required headers are not configured`() {
            folderRule.newFolder("avro")
            folderRule.newFile("avro/test.avsc").writeText(avroSchema)

            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_HEADERS_PORT/'
                compatibility {
                    subject('testSubject1', 'avro/test.avsc')
                }
            }
            """.trimIndent()
            )

            val result: BuildResult? = failTask(CompatibilityTask.TASK_NAME)

            Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }
}
