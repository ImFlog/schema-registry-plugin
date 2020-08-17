package com.github.imflog.schema.registry.compatibility

import com.github.imflog.schema.registry.TestContainersUtils
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class CompatibilityTaskIT : TestContainersUtils() {
    private lateinit var folderRule: TemporaryFolder
    private lateinit var buildFile: File

    private val subject = "test-subject"

    private val defaultSchema = """{
        "type": "record",
        "name": "User",
        "fields":[
            {
                "name": "name",
                "type": "string"
            }
        ]
    }"""

    @BeforeEach
    fun init() {
        // Reset the client before each test
        folderRule = TemporaryFolder()

        client.register(subject, AvroSchema(defaultSchema))
    }

    @AfterEach
    internal fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `CompatibilityTask should validate input schema with no dependencies`() {
        folderRule.create()
        folderRule.newFolder("avro")
        val testAvsc = folderRule.newFile("avro/test.avsc")
        val schemaTest = """
            {
                "type": "record",
                "name": "User",
                "fields": [
                    {
                        "name": "name",
                        "type": "string"
                    },
                    {
                        "name": "nickname",
                        "type": ["null", "string"],
                        "default": null
                    }
                ]
            }
        """
        testAvsc.writeText(schemaTest)

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                compatibility {
                    subject('$subject', 'avro/test.avsc')
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
                url = '$schemaRegistryEndpoint'
                compatibility {
                    subject('$subject', 'avro/test.avsc', "AVRO").addReference('Address', 'Address', 1)
                }
            }
        """
        )

        client.register("Address", AvroSchema("""
            {
                "type": "record",
                "name": "Address",
                "fields": [
                    {
                        "name": "street",
                        "type": "string"
                    }
                ]
            }
        """))

        folderRule.newFolder("avro")
        val testAvsc = folderRule.newFile("avro/test.avsc")
        val schemaTest = """
            {
                "type":"record",
                "name":"User",
                "fields":[
                    {
                        "name": "name",
                        "type": "string"
                    },
                    {
                        "name": "address",
                        "type": ["null", "Address"],
                        "default": null
                    }
                ]
            }
        """
        testAvsc.writeText(schemaTest)

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
