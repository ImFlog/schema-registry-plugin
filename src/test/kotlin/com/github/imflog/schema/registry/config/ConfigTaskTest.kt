package com.github.imflog.schema.registry.config

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

class ConfigTaskTest {
    lateinit var folderRule: TemporaryFolder

    lateinit var buildFile: File

    val username: String = "user"
    val password: String = "pass"

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
            WireMock.put(
                WireMock
                    .urlMatching("/config/.*")
            )
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody("""{ "compatibility": "FULL_TRANSITIVE" }""")
                )
        )

        // Stub with authentication configuration
        wiremockAuthServerItem.stubFor(
            WireMock.put(
                WireMock
                    .urlMatching("/config/.*")
            )
                .withBasicAuth(username, password)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody("""{ "compatibility": "FULL_TRANSITIVE" }""")
                )
        )
    }

    @AfterEach
    internal fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `ConfigTask should set subject compatibility`() {
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
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("4.9")
            .withProjectDir(folderRule.root)
            .withArguments(CONFIG_SUBJECTS_TASK)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    }

    @Test
    fun `ConfigTask should set subject compatibility without credentials`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_PORT/'
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("4.9")
            .withProjectDir(folderRule.root)
            .withArguments(CONFIG_SUBJECTS_TASK)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `ConfigTask should fail to set subject compatibility without credentials when required`() {
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
                config {
                    subject('testSubject1', 'FULL_TRANSITIVE')
                }
            }
            """.trimIndent()
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("4.9")
            .withProjectDir(folderRule.root)
            .withArguments(CONFIG_SUBJECTS_TASK)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":configSubjectsTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

}