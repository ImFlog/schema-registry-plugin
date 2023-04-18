package com.github.imflog.schema.registry.tasks.download

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DownloadTaskActionTest {
    @TempDir
    lateinit var folderRule: Path

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

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(
                DownloadSubject(testSubject, outputDir),
                DownloadSubject(fooSubject, outputDir)
            ),
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc")).isNotNull
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .containsIgnoringCase("test")
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/foo.avsc")).isNotNull
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/foo.avsc").readText())
            .containsIgnoringCase("foo")
    }

    @Test
    fun `Should download schemas by subject name pattern`() {
        // given
        val testSubject = "test"
        val teaSubject = "tea"
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
            teaSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "tea",
                    "fields": [{ "name": "name", "type": "string" }]
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

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(
                DownloadSubject("te.*", outputDir, null, true)
            ),
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc")).exists()
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .containsIgnoringCase("test")
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/tea.avsc")).exists()
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/tea.avsc").readText())
            .containsIgnoringCase("tea")
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/foo.avsc")).doesNotExist()
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

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(DownloadSubject(subject, outputDir)),
        ).run()

        // then
        Assertions.assertThat(errorCount).isGreaterThan(0)
    }

    @Test
    fun `Should ignore invalid subject name regex`() {
        // given
        val invalidSubjectPattern = "oups("
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

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(
                DownloadSubject(invalidSubjectPattern, outputDir, null, true),
                DownloadSubject("test", outputDir)
            ),
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc")).exists()
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .containsIgnoringCase("test")
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

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // When
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(
                DownloadSubject("test", outputDir, v1Id)
            ),
        ).run()

        // Then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc")).isNotNull
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .containsIgnoringCase("test")
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .doesNotContain("desc")
    }
}
