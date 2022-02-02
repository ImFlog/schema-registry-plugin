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

class BasicAuthenticationIT {

    private lateinit var folderRule: TemporaryFolder
    private lateinit var buildFile: File

    private val defaultSchema = """{
        "type":"record",
        "name":"Dependency",
        "fields":[
            {
                "name":"name",
                "type":"string"
            }
        ]
    }"""

    companion object {
        private const val REGISTRY_FAKE_AUTH_PORT = 7777
        private const val USERNAME: String = "user"
        private const val PASSWORD: String = "pass"

        private val mapper = ObjectMapper()

        lateinit var wireMockAuthServerItem: WireMockServer

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            wireMockAuthServerItem = WireMockServer(
                WireMockConfiguration.wireMockConfig()
                    .port(REGISTRY_FAKE_AUTH_PORT)
                    .notifier(ConsoleNotifier(true))
            )
            wireMockAuthServerItem.start()
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            wireMockAuthServerItem.stop()
        }
    }

    @BeforeEach
    fun init() {
        // Reset the client before each test
        folderRule = TemporaryFolder()
        folderRule.create()
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
        wireMockAuthServerItem.resetAll()
    }

    @Nested
    inner class ConfigurationTest {

        @BeforeEach
        fun setup() {
            // Stub with authentication configuration
            wireMockAuthServerItem.stubFor(
                WireMock.put(
                    WireMock.urlMatching("/config/.*")
                )
                    .withBasicAuth(USERNAME, PASSWORD)
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody("""{ "compatibility": "FULL_TRANSITIVE" }""")
                    )
            )
        }

        @Test
        fun `ConfigTask should set subject compatibility`() {
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
                    username = '$USERNAME'
                    password = '$PASSWORD'
                }
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
        fun `ConfigTask should fail to set subject compatibility without credentials when required`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_AUTH_PORT/'
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
                .buildAndFail()
            Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    @Nested
    inner class CompatibilityTest {

        @BeforeEach
        fun setUp() {
            // Stub with authentication configuration
            wireMockAuthServerItem.stubFor(
                WireMock.post(
                    WireMock.urlMatching("/compatibility/subjects/.*/versions/.*")
                )
                    .withBasicAuth(USERNAME, PASSWORD)
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/vnd.schemaregistry.v1+json")
                            .withBody("{\"is_compatible\": true}")
                    )
            )

            val schema = Schema("Dependency", 1, 1, AvroSchema.TYPE, listOf(), defaultSchema)
            wireMockAuthServerItem.stubFor(
                WireMock.get(WireMock.urlMatching("/subjects/.*/versions/1.*"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody(mapper.writeValueAsString(schema))
                    )
            )
        }

        @Test
        fun `Compatibility task should fail if credential not provided on authenticated registry`() {
            folderRule.newFolder("avro")
            val testAvsc = folderRule.newFile("avro/test.avsc")
            val schemaTest = """
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
            testAvsc.writeText(schemaTest)

            buildFile = folderRule.newFile("build.gradle")
            // Auth configuration is omited to test the failure
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_AUTH_PORT/'
                compatibility {
                    subject('testSubject1', 'avro/test.avsc')
                }
            }
        """
            )

            val result: BuildResult? = GradleRunner.create()
                .withGradleVersion("6.7.1")
                .withProjectDir(folderRule.root)
                .withArguments(CompatibilityTask.TASK_NAME)
                .withPluginClasspath()
                .withDebug(true)
                .buildAndFail()
            Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }

        @Nested
        inner class ConfigurationTest {

            @BeforeEach
            fun setup() {
                // Stub with authentication configuration
                wireMockAuthServerItem.stubFor(
                    WireMock.put(
                        WireMock.urlMatching("/config/.*")
                    )
                        .withBasicAuth(USERNAME, PASSWORD)
                        .willReturn(
                            WireMock.aResponse()
                                .withStatus(200)
                                .withBody("""{ "compatibility": "FULL_TRANSITIVE" }""")
                        )
                )
            }

            @Test
            fun `ConfigTask should set subject compatibility`() {
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
                    username = '$USERNAME'
                    password = '$PASSWORD'
                }
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
            fun `ConfigTask should fail to set subject compatibility without credentials when required`() {
                buildFile = folderRule.newFile("build.gradle")
                buildFile.writeText(
                    """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_AUTH_PORT/'
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
                    .buildAndFail()
                Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
            }
        }

        @Test
        fun `CompatibilityTask should validate input schema with no references`() {
            folderRule.newFolder("avro")
            val testAvsc = folderRule.newFile("avro/test.avsc")
            val schemaTest = """
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
            testAvsc.writeText(schemaTest)

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
                    username = '$USERNAME'
                    password = '$PASSWORD'
                }
                compatibility {
                    subject('testSubject1', 'avro/test.avsc')
                }
            }
        """
            )

            val result: BuildResult? = GradleRunner.create()
                .withGradleVersion("6.7.1")
                .withProjectDir(folderRule.root)
                .withArguments(CompatibilityTask.TASK_NAME)
                .withPluginClasspath()
                .withDebug(true)
                .build()
            Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Nested
    inner class DownloadTest {
        private val subject = "test-subject"
        val schema =
            "{\"type\": \"record\", \"name\": \"Blah\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}"

        @BeforeEach
        fun setUp() {
            // Stub with authentication configuration
            wireMockAuthServerItem.stubFor(
                WireMock.get(
                    WireMock.urlMatching("/subjects/test-subject/versions/latest")
                )
                    .withBasicAuth(USERNAME, PASSWORD)
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Accept", "application/json")
                            .withBody(mapper.writeValueAsString(Schema(subject, 1, 1, "AVRO", emptyList(), schema)))
                    )
            )
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
                    username = '$USERNAME'
                    password = '$PASSWORD'
                }
                download {
                    subject('test-subject', 'src/main/avro/test')
                }
            }
        """
            )

            val result: BuildResult? = GradleRunner.create()
                .withGradleVersion("6.7.1")
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
                .withGradleVersion("6.7.1")
                .withProjectDir(folderRule.root)
                .withArguments(DownloadTask.TASK_NAME)
                .withPluginClasspath()
                .withDebug(true)
                .buildAndFail()
            Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    @Nested
    inner class RegisterTest {

        @BeforeEach
        fun setup() {
            wireMockAuthServerItem.stubFor(
                WireMock.post(WireMock.urlMatching("/subjects/.*/versions\\?normalize=false"))
                    .withBasicAuth(USERNAME, PASSWORD)
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody("{\"id\": 1}")
                    )
            )
            // Stub for get subject reference
            val schema = Schema(
                "testSubject1", 1, 1, AvroSchema.TYPE, listOf(), """{
                "type":"record",
                "name":"Blah",
                "fields":[
                    {
                        "name":"name",
                        "type":"string"
                    }
                ]
            }"""
            )
            wireMockAuthServerItem.stubFor(
                WireMock.get(WireMock.urlMatching("/subjects/.*/versions/1.*"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody(mapper.writeValueAsString(schema))
                    )
            )
        }

        @Test
        fun `RegisterSchemasTask should register schemas`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
            
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_AUTH_PORT'
                credentials {
                    username = '$USERNAME'
                    password = '$PASSWORD'
                }
                register {
                    subject('testSubject1', 'avro/test.avsc')
                    subject('testSubject2', 'avro/other_test.avsc')
                    subject('testSubject3', 'avro/dependency_test.avsc', "AVRO").addReference('Blah', 'testSubject1', 1)
                }
            }
        """
            )

            folderRule.newFolder("avro")
            val testAvsc = folderRule.newFile("avro/test.avsc")
            val schemaTest = """
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
            testAvsc.writeText(schemaTest)

            val testAvsc2 = folderRule.newFile("avro/other_test.avsc")
            testAvsc2.writeText(schemaTest)

            val depAvsc = folderRule.newFile("avro/dependency_test.avsc")
            val depSchema = """
            {
                "type":"record",
                "name":"Dependency",
                "fields":[
                    {
                        "name":"blah",
                        "type":"Blah"
                    }
                ]
            }
        """.trimIndent()
            depAvsc.writeText(depSchema)

            val result: BuildResult? = GradleRunner.create()
                .withGradleVersion("6.7.1")
                .withProjectDir(folderRule.root)
                .withArguments(RegisterSchemasTask.TASK_NAME)
                .withPluginClasspath()
                .withDebug(true)
                .build()
            Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        @Test
        fun `RegisterSchemasTask should fail register schemas without credentials when required`() {
            buildFile = folderRule.newFile("build.gradle")
            buildFile.writeText(
                """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_AUTH_PORT/'
                register {
                    subject('testSubject1', 'avro/test.avsc')
                }
            }
        """
            )

            folderRule.newFolder("avro")
            val testAvsc = folderRule.newFile("avro/test.avsc")
            val schemaTest = """
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
            testAvsc.writeText(schemaTest)

            val result: BuildResult? = GradleRunner.create()
                .withGradleVersion("6.7.1")
                .withProjectDir(folderRule.root)
                .withArguments(RegisterSchemasTask.TASK_NAME)
                .withPluginClasspath()
                .withDebug(true)
                .buildAndFail()
            Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }
}
