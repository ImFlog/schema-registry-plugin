package com.github.imflog.schema.registry

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.*
import java.io.File

const val REGISTRY_FAKE_PORT = 6666

class DownloadSchemaTaskTest {

    lateinit var folderRule: TemporaryFolder
    val subject = "test-subject"

    val schema = "{\"type\": \"record\", \"name\": \"Blah\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}"
    lateinit var buildFile: File

    val mapper = ObjectMapper()

    companion object {
        lateinit var wiremockServerItem: WireMockServer

        @BeforeClass
        @JvmStatic
        fun initClass() {
            wiremockServerItem = WireMockServer(
                    WireMockConfiguration
                            .wireMockConfig()
                            .port(REGISTRY_FAKE_PORT)
                            .notifier(ConsoleNotifier(true)))
            wiremockServerItem.start()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            wiremockServerItem.stop()
        }
    }

    @Before
    fun init() {
        folderRule = TemporaryFolder()
        // Register schema
        val avroSchema = Schema(subject, 1, 1, schema)
        wiremockServerItem.stubFor(
                WireMock.get(WireMock
                        .urlMatching("/subjects/.*/versions/latest"))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withBody(mapper.writeValueAsString(avroSchema))))
    }

    @After
    internal fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `DownloadSchemaTask should download last schema version`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText("""
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_PORT/'
                output = 'src/main/avro'
                subjects = ['$subject']
            }
        """)

        val result: BuildResult? = GradleRunner.create()
                .withGradleVersion("4.4")
                .withProjectDir(folderRule.root)
                .withArguments(DOWNLOAD_SCHEMA_TASK)
                .withPluginClasspath()
                .withDebug(true)
                .build()

        assertThat(File(folderRule.root, "src/main/avro")).exists()
        assertThat(File(folderRule.root, "src/main/avro/test-subject.avsc")).exists()
        assertThat(result?.task(":downloadSchemaTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `DownloadSchemaTask should fail with wrong extension configuration`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText("""
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                urlFoo = 'http://localhost:$REGISTRY_FAKE_PORT/'
                output = 'src/main/avro'
                subjects = ['$subject']
            }
        """)

        try {
            GradleRunner.create()
                    .withGradleVersion("4.4")
                    .withProjectDir(folderRule.root)
                    .withArguments(DOWNLOAD_SCHEMA_TASK)
                    .withPluginClasspath()
                    .withDebug(true)
                    .build()
            Assertions.fail("Should not reach this point")
        } catch (ex: UnexpectedBuildFailure) {
            Assertions.assertThat(ex.message).containsIgnoringCase("unknown property 'urlFoo'")
        }
    }
}