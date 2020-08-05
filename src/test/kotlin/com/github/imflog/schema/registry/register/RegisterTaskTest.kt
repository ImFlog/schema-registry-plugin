package com.github.imflog.schema.registry.register

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.imflog.schema.registry.REGISTRY_FAKE_AUTH_PORT
import com.github.imflog.schema.registry.REGISTRY_FAKE_PORT
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

class RegisterTaskTest {
    lateinit var folderRule: TemporaryFolder

    lateinit var buildFile: File

    val username: String = "user"
    val password: String = "pass"

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
        // Stub without authentication configuration
        wiremockServerItem.stubFor(
            WireMock
                .post(WireMock.urlMatching("/subjects/.*/versions"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody("{\"id\": 1}")
                )
        )
        // Stub with authentication configuration
        wiremockAuthServerItem.stubFor(
            WireMock
                .post(WireMock.urlMatching("/subjects/.*/versions"))
                .withBasicAuth(username, password)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody("{\"id\": 1}")
                )
        )
        // Stub for get subject dependencies
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
        wiremockAuthServerItem.stubFor(
            WireMock
                .get(WireMock.urlMatching("/subjects/.*/versions/1.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody(mapper.writeValueAsString(schema))
                )
        )

        wiremockServerItem.stubFor(
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
    fun `RegisterSchemasTask should register schemas`() {
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
                register {
                    subject('testSubject1', 'avro/test.avsc')
                    subject('testSubject2', 'avro/other_test.avsc')
                    subject('testSubject3', 'avro/dependency_test.avsc', "AVRO", new SchemaReference('testSubject1','testSubject1', 1))
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
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(RegisterSchemasTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `RegisterSchemasTask should register schemas without credentials`() {
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
                url = 'http://localhost:$REGISTRY_FAKE_PORT/'
                register {
                    subject('testSubject1', 'avro/test.avsc')
                    subject('testSubject2', 'avro/other_test.avsc')
                    subject('testSubject3', 'avro/dependency_test.avsc', "AVRO", new SchemaReference('testSubject1', 'testSubject1', 1))
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
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(RegisterSchemasTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `RegisterSchemasTask should fail register schemas without credentials when required`() {
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
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(RegisterSchemasTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }
}
