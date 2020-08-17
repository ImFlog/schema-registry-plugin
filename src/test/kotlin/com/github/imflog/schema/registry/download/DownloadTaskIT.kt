package com.github.imflog.schema.registry.download

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

class DownloadTaskIT : TestContainersUtils() {

    private lateinit var folderRule: TemporaryFolder
    private val subject = "test-subject"

    private val schemaOld = """{
        "type": "record",
        "name": "User",
        "fields": [
            { "name": "name", "type": "string" }
        ]
    }"""

    private val schema = """{
        "type": "record",
        "name": "User",
        "fields": [
            { "name": "name", "type": "string" }, 
            { "name": "description", "type": ["null", "string"], "default": null }
        ]
    }"""

    private lateinit var buildFile: File

    @BeforeEach
    fun init() {
        // Register schema
        client.register(subject, AvroSchema(schemaOld))
        client.register(subject, AvroSchema(schema))

        folderRule = TemporaryFolder()
        folderRule.create()
    }

    @AfterEach
    fun tearDown() {
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
                url = '$schemaRegistryEndpoint'
                download {
                    subject('$subject', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
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
    fun `DownloadSchemaTask should fail download when schema does not exist`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subject('UNKNOWN', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    fun `DownloadSchemaTask should download specific schema version`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subject('$subject', 'src/main/avro/test_v1', 1)
                    subject('$subject', 'src/main/avro/test_v2', 2)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.2.2")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        Assertions.assertThat(File(folderRule.root, "src/main/avro/test_v1")).exists()
        val resultFile1 = File(folderRule.root, "src/main/avro/test_v1/test-subject.avsc")
        Assertions.assertThat(resultFile1).exists()
        Assertions.assertThat(resultFile1.readText()).doesNotContain("desc")

        Assertions.assertThat(File(folderRule.root, "src/main/avro/test_v2")).exists()
        val resultFile2 = File(folderRule.root, "src/main/avro/test_v2/test-subject.avsc")
        Assertions.assertThat(resultFile2).exists()
        Assertions.assertThat(resultFile2.readText()).contains("desc")
    }
}
