package com.github.imflog.schema.registry

import com.github.imflog.schema.registry.download.DOWNLOAD_SCHEMAS_TASK
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

const val REGISTRY_FAKE_PORT = 6666

class SchemaRegistryPluginTest {
    lateinit var project: Project
    lateinit var folderRule: TemporaryFolder

    val subject = "test-subject"

    lateinit var buildFile: File

    @BeforeEach
    fun init() {
        folderRule = TemporaryFolder()
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(SchemaRegistryPlugin::class.java)
    }

    @AfterEach
    internal fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `plugin should add tasks when applied`() {
        project.afterEvaluate {
            val downloadSchemaTask = project.tasks.getByName(DOWNLOAD_SCHEMAS_TASK)
            assertThat(downloadSchemaTask).isNotNull()
        }
    }

    @Test
    fun `plugin should fail with wrong url extension configuration`() {
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
                    .withGradleVersion("4.9")
                    .withProjectDir(folderRule.root)
                    .withArguments(DOWNLOAD_SCHEMAS_TASK)
                    .withPluginClasspath()
                    .withDebug(true)
                    .build()
            Assertions.fail("Should not reach this point")
        } catch (ex: UnexpectedBuildFailure) {
            Assertions.assertThat(ex.message).containsIgnoringCase("unknown property 'urlFoo'")
        }
    }

    @Test
    fun `plugin should fail with wrong credentials extension configuration`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText("""
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_PORT/'
                credentials {
                    username = 'user'
                    password = 'pass'
                }
                credentialsBar.username = 'user'
                output = 'src/main/avro'
                subjects = ['$subject']
            }
        """)

        try {
            GradleRunner.create()
                    .withGradleVersion("4.9")
                    .withProjectDir(folderRule.root)
                    .withArguments(DOWNLOAD_SCHEMAS_TASK)
                    .withPluginClasspath()
                    .withDebug(true)
                    .build()
            Assertions.fail("Should not reach this point")
        } catch (ex: UnexpectedBuildFailure) {
            Assertions.assertThat(ex.message).containsIgnoringCase("unknown property 'credentialsBar'")
        }
    }
}