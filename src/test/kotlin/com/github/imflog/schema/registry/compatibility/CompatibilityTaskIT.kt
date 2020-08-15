package com.github.imflog.schema.registry.compatibility

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

class CompatibilityTaskIT: TestContainersUtils() {
    lateinit var folderRule: TemporaryFolder
    lateinit var buildFile: File

    private val username: String = "user"

    private val password: String = "pass"

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

    // Refacto this when switching to TestContainers.
    private val mapper = ObjectMapper()

    companion object {
        lateinit var wireMockAuthServerItem: WireMockServer

        @BeforeAll
        @JvmStatic
        fun initClass() {
            wireMockAuthServerItem = WireMockServer(
                WireMockConfiguration
                    .wireMockConfig()
                    .port(REGISTRY_FAKE_AUTH_PORT)
                    .notifier(ConsoleNotifier(true))
            )
            wireMockAuthServerItem.start()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            wireMockAuthServerItem.stop()
        }
    }

    @BeforeEach
    fun init() {
        // Reset the client before each test
        folderRule = TemporaryFolder()

        client.register("Dependency", AvroSchema(defaultSchema))

        // Stub with authentication configuration
        wireMockAuthServerItem.stubFor(
            WireMock.post(
                WireMock
                    .urlMatching("/compatibility/subjects/.*/versions/.*")
            )
                .withBasicAuth(username, password)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/vnd.schemaregistry.v1+json")
                        .withBody("{\"is_compatible\": true}")
                )
        )

        val schema = Schema("Dependency", 1, 1, AvroSchema.TYPE, listOf(), defaultSchema)
        wireMockAuthServerItem.stubFor(
            WireMock
                .get(WireMock.urlMatching("/subjects/.*/versions/1.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody(mapper.writeValueAsString(schema))
                )
        )
    }

    @AfterEach
    internal fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `CompatibilityTask should validate input schema with no dependencies`() {
        folderRule.create()
        folderRule.newFolder("avro")
        val testAvsc = folderRule.newFile("avro/other_test.avsc")
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

        val testAvsc2 = folderRule.newFile("avro/test.avsc")
        testAvsc2.writeText(schemaTest)

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
                compatibility {
                    subject('testSubject1', 'avro/test.avsc')
                    subject('testSubject2', 'avro/other_test.avsc')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `CompatibilityTask should validate input schema with dependencies`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference

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
                compatibility {
                    subject('testSubject', 'avro/core.avsc', "AVRO").addReference('Dependency', 'Dependency', 1)
                }
            }
        """
        )

        folderRule.newFolder("avro")
        val coreAvsc = folderRule.newFile("avro/core.avsc")
        val coreSchema = """
            {
                "type":"record",
                "name":"Core",
                "fields":[
                    {
                        "name":"dep",
                        "type":"Dependency"
                    }
                ]
            }
        """.trimIndent()
        coreAvsc.writeText(coreSchema)

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    internal fun `Compatibility task should fail if credential not provided on authenticated registry`() {
        folderRule.create()
        folderRule.newFolder("avro")
        val testAvsc = folderRule.newFile("avro/other_test.avsc")
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

        val testAvsc2 = folderRule.newFile("avro/test.avsc")
        testAvsc2.writeText(schemaTest)

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
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    fun `CompatibilityTask should validate input schema with dependencies without credentials`() {
        folderRule.create()
        folderRule.newFolder("avro")
        val testAvsc = folderRule.newFile("avro/other_test.avsc")
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

        val testAvsc2 = folderRule.newFile("avro/test.avsc")
        testAvsc2.writeText(schemaTest)

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                credentials {
                    username = '$username'
                    password = '$password'
                }
                compatibility {
                    subject('testSubject1', 'avro/test.avsc')
                    subject('testSubject2', 'avro/other_test.avsc')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
