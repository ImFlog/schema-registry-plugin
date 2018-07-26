package com.github.imflog.schema.registry.download

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.apache.avro.Schema
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class DownloadTaskActionTest {
    lateinit var folderRule: TemporaryFolder

    @Before
    fun setUp() {
        folderRule = TemporaryFolder()
        folderRule.create()
    }

    @After
    fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `Should download schemas`() {
        // given
        val subjects = mutableListOf("test", "foo")

        val parser = Schema.Parser()
        val testSchema = parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val fooSchema = parser.parse("{\"type\": \"record\", \"name\": \"foo\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")

        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)
        registryClient.register("foo", fooSchema)

        val outputDir = File(folderRule.root, "src/main/avro/external")
        folderRule.newFolder("src", "main", "avro", "external")

        // when
        val errorCount = DownloadTaskAction(
                registryClient,
                subjects,
                outputDir
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(outputDir, "test.avsc")).isNotNull()
        Assertions.assertThat(File(outputDir, "test.avsc").readText()).containsIgnoringCase("test")
        Assertions.assertThat(File(outputDir, "foo.avsc")).isNotNull()
        Assertions.assertThat(File(outputDir, "foo.avsc").readText()).containsIgnoringCase("foo")
    }

    @Test
    fun `Should fail on missing schema`() {
        // given
        val subjects = mutableListOf("oups")

        val parser = Schema.Parser()
        val testSchema = parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val fooSchema = parser.parse("{\"type\": \"record\", \"name\": \"foo\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")

        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)
        registryClient.register("foo", fooSchema)

        val outputDir = File(folderRule.root, "src/main/avro/external")
        folderRule.newFolder("src", "main", "avro", "external")

        // when
        val errorCount = DownloadTaskAction(
                registryClient,
                subjects,
                outputDir
        ).run()

        // then
        Assertions.assertThat(errorCount).isGreaterThan(0)
    }
}