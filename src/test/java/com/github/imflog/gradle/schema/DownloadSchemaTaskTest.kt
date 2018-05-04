package com.github.imflog.gradle.schema

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.apache.avro.Schema
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Matchers.anyString
import org.mockito.Mockito
import java.io.File

class DownloadSchemaTaskTest {

    @get:Rule
    val folderRule: TemporaryFolder = TemporaryFolder()

    val subject = "test-subject"
    val schema = "{\"type\": \"record\", \"name\": \"Blah\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}"
    val url = "http://localhost:8088"

    lateinit var buildFile: File

    @Test
    fun `DownloadSchemaTask should download last schema version`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText("""
            plugins {
                id 'java'
                id 'com.github.imflog.schema-registry'
            }

            schemaRegistry {
                url = '$url'
                output = 'src/main/avro'
                subjects = ['$subject']
            }
        """)


        // Mocking doesn't work as we fire a new JVM ?
        // TODO : use testContainers ?
        val mockSchemaRegistry = MockSchemaRegistryClient()
        val parser = Schema.Parser()
        mockSchemaRegistry.register(subject, parser.parse(schema))
        val mockRegistryClientWrapper = Mockito.mock(RegistryClientWrapper::class.java)
        Mockito.`when`(mockRegistryClientWrapper.client(anyString())).thenReturn(mockSchemaRegistry)


        val result: BuildResult? = GradleRunner.create()
                .withGradleVersion("4.4")
                .withProjectDir(folderRule.root)
                .withArguments("build")
                .withPluginClasspath()
                .withDebug(true)
                .build()

        assertThat(File(folderRule.root, "src/main/avro")).exists()
        assertThat(File(folderRule.root, "src/main/avro/test-subject.avsc")).exists()
        assertThat(result?.task(":downloadSchemaTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

//    @Ignore
//    @Test
//    fun `DownloadSchemaTask should fail with wrong extension configuration`() {
//        TODO("Implement this test")
//    }
}