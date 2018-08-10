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
        val testSubject = "test"
        val fooSubject = "foo"
        val outputDir = "src/main/avro/external"

        val parser = Schema.Parser()
        val testSchema = parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val fooSchema = parser.parse("{\"type\": \"record\", \"name\": \"foo\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")

        val registryClient = MockSchemaRegistryClient()
        registryClient.register(testSubject, testSchema)
        registryClient.register(fooSubject, fooSchema)

        folderRule.newFolder("src", "main", "avro", "external")

        // when
        val errorCount = DownloadTaskAction(
                registryClient,
                arrayListOf(
                        Pair(testSubject, outputDir),
                        Pair(fooSubject, outputDir)
                ),
                folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/test.avsc")).isNotNull()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/test.avsc").readText()).containsIgnoringCase("test")
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/foo.avsc")).isNotNull()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/foo.avsc").readText()).containsIgnoringCase("foo")
    }

    @Test
    fun `Should fail on missing schema`() {
        // given
        val subject = "oups"
        val outputDir = "src/main/avro/external"

        val parser = Schema.Parser()
        val testSchema = parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val fooSchema = parser.parse("{\"type\": \"record\", \"name\": \"foo\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")

        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)
        registryClient.register("foo", fooSchema)

        folderRule.newFolder("src", "main", "avro", "external")

        // when
        val errorCount = DownloadTaskAction(
                registryClient,
                arrayListOf(Pair(subject, outputDir)),
                folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isGreaterThan(0)
    }
}