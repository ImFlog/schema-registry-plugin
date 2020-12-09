package com.github.imflog.schema.registry.tasks.download

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class DownloadTaskActionTest {
    lateinit var folderRule: TemporaryFolder

    @BeforeEach
    fun setUp() {
        folderRule = TemporaryFolder()
        folderRule.create()
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `Should download schemas`() {
        // given
        val testSubject = "test"
        val fooSubject = "foo"
        val outputDir = "src/main/avro/external"

        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))

        registryClient.register(
            testSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )
        registryClient.register(
            fooSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "foo",
                    "fields": [{ "name": "name", "type": "string" }]
                }""",
                listOf()
            ).get()
        )

        folderRule.newFolder("src", "main", "avro", "external")

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRule.root,
            arrayListOf(
                DownloadSubject(testSubject, outputDir),
                DownloadSubject(fooSubject, outputDir)
            )
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/test.avsc")).isNotNull()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/test.avsc").readText())
            .containsIgnoringCase("test")
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/foo.avsc")).isNotNull()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/foo.avsc").readText())
            .containsIgnoringCase("foo")
    }

    @Test
    fun `Should fail on missing schema`() {
        // given
        val subject = "oups"
        val outputDir = "src/main/avro/external"

        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))

        registryClient.register(
            "test",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{"type": "record", "name": "test", "fields": [{ "name": "name", "type": "string" }]}""",
                listOf()
            ).get()
        )
        registryClient.register(
            "foo",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{"type": "record", "name": "foo", "fields": [{ "name": "name", "type": "string" }]}""",
                listOf()
            ).get()
        )

        folderRule.newFolder("src", "main", "avro", "external")

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRule.root,
            arrayListOf(DownloadSubject(subject, outputDir))
        ).run()

        // then
        Assertions.assertThat(errorCount).isGreaterThan(0)
    }

    @Test
    fun `Should download a specific version`() {
        // Given
        val testSubject = "test"
        val outputDir = "src/main/avro/external"

        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))

        val v1Id = registryClient.register(
            testSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{"type": "record", "name": "test", "fields": [{ "name": "name", "type": "string" }]}""",
                listOf()
            ).get()
        )

        val v2Id = registryClient.register(
            testSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                "type": "record",
                "name": "test",
                "fields": [
                   { "name": "name", "type": "string" },
                   { "name": "desc", "type": "string" }
                ]}""",
                listOf()
            ).get()
        )
        Assertions.assertThat(v1Id).isNotEqualTo(v2Id)

        folderRule.newFolder("src", "main", "avro", "external")

        // When
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRule.root,
            arrayListOf(
                DownloadSubject("test", outputDir, v1Id)
            )
        ).run()

        // Then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/test.avsc")).isNotNull()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/test.avsc").readText())
            .containsIgnoringCase("test")
        Assertions.assertThat(File(folderRule.root, "src/main/avro/external/test.avsc").readText())
            .doesNotContain("desc")
    }
}
