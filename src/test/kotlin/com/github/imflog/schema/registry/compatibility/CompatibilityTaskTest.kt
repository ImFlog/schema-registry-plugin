package com.github.imflog.schema.registry.compatibility

import com.github.imflog.schema.registry.REGISTRY_FAKE_AUTH_PORT
import com.github.imflog.schema.registry.REGISTRY_FAKE_PORT
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
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

class CompatibilityTaskTest {
    lateinit var folderRule: TemporaryFolder
    lateinit var buildFile: File

    val username: String = "user"

    val password: String = "pass"

    companion object {
        lateinit var wireMockServerItem: WireMockServer
        lateinit var wireMockAuthServerItem: WireMockServer

        @BeforeAll
        @JvmStatic
        fun initClass() {
            wireMockServerItem = WireMockServer(
                WireMockConfiguration
                    .wireMockConfig()
                    .port(REGISTRY_FAKE_PORT)
                    .notifier(ConsoleNotifier(true))
            )
            wireMockServerItem.start()

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
            wireMockServerItem.stop()
            wireMockAuthServerItem.stop()
        }
    }

    @BeforeEach
    fun init() {
        // Reset the client before each test
        folderRule = TemporaryFolder()
        // Stub without authentication configuration
        wireMockServerItem.stubFor(
            WireMock.post(
                    WireMock
                        .urlMatching("/compatibility/subjects/.*/versions/.*")
                )
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/vnd.schemaregistry.v1+json")
                        .withBody("{\"is_compatible\": true}")
                )
        )

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
                    subject('testSubject', 'avro/core.avsc', ['avro/dependency.avsc'])
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

        val depAvsc = folderRule.newFile("avro/dependency.avsc")
        val depSchema = """
            {
                "type":"record",
                "name":"Dependency",
                "fields":[
                    {
                        "name":"name",
                        "type":"string"
                    }
                ]
            }
        """.trimIndent()
        depAvsc.writeText(depSchema)

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
                url = 'http://localhost:$REGISTRY_FAKE_PORT/'
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
