package com.github.imflog.schema.registry.tasks.download

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
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
            MetadataExtension()
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .containsIgnoringCase("test")
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/foo.avsc").exists()).isTrue
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
            MetadataExtension()
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
            MetadataExtension()
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
            MetadataExtension()
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
            MetadataExtension()
        ).run()

        // Then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .containsIgnoringCase("test")
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .doesNotContain("desc")
    }

    @Test
    fun `Should download schemas with metadata in the same output dir by default`() {
        // given
        val testSubject = "test"
        val outputDir = "src/main/avro/external"

        val registryClient = MockSchemaRegistryClient(listOf(AvroSchemaProvider()))

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

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(DownloadSubject(testSubject, outputDir)),
            MetadataExtension(true)
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test-metadata.json").exists()).isTrue
        // Would be cleaner to use a JSON assertion library but I am not sure this is really required for now
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test-metadata.json").readText())
            .containsIgnoringCase("\"id\" :")
            .containsIgnoringCase("\"version\" :")
            .containsIgnoringCase("\"schema_type\" :")
            .containsIgnoringCase("\"schema\" :")
            .containsIgnoringCase("\"references\" :")
    }

    @Test
    fun `Should download schemas with metadata in the specified output dir`() {
        // given
        val testSubject = "test"
        val outputDir = "src/main/avro/external"
        val metadataDir = "src/main/avro/metadata"

        val registryClient = MockSchemaRegistryClient(listOf(AvroSchemaProvider()))

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

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(DownloadSubject(testSubject, outputDir)),
            MetadataExtension(true, metadataDir)
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/metadata/test-metadata.json").exists()).isTrue
        // Would be cleaner to use a JSON assertion library but I am not sure this is really required for now
        Assertions.assertThat(File(folderRoot, "src/main/avro/metadata/test-metadata.json").readText())
            .containsIgnoringCase("\"id\" :")
            .containsIgnoringCase("\"version\" :")
            .containsIgnoringCase("\"schema_type\" :")
            .containsIgnoringCase("\"schema\" :")
            .containsIgnoringCase("\"references\" :")
    }

    @Test
    fun `Should download referenced schemas with metadata in the specified output dir`() {
        // given
        val testSubject = "test"
        val testLibSubject = "test_lib"
        val outputDir = "src/main/avro/external"
        val metadataDir = "src/main/avro/metadata"

        val registryClient = MockSchemaRegistryClient(listOf(AvroSchemaProvider()))

        registryClient.register(
            testLibSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test_lib",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        registryClient.register(
            testSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test",
                    "fields": [
                        { "name": "name", "type": "test_lib" }
                    ]
                }""",
                listOf(
                    SchemaReference(
                        "name",
                        testLibSubject,
                        1
                    )
                )
            ).get()
        )

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(DownloadSubject(testSubject, outputDir, downloadReferences = true)),
            MetadataExtension(true, metadataDir)
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/metadata/test-metadata.json").exists()).isTrue
        // Would be cleaner to use a JSON assertion library but I am not sure this is really required for now
        Assertions.assertThat(File(folderRoot, "src/main/avro/metadata/test-metadata.json").readText())
            .containsIgnoringCase("\"id\" :")
            .containsIgnoringCase("\"version\" :")
            .containsIgnoringCase("\"schema_type\" :")
            .containsIgnoringCase("\"schema\" :")
            .containsIgnoringCase("\"references\" :")

        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test_lib.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/metadata/test_lib-metadata.json").exists()).isTrue
        // Would be cleaner to use a JSON assertion library but I am not sure this is really required for now
        Assertions.assertThat(File(folderRoot, "src/main/avro/metadata/test_lib-metadata.json").readText())
            .containsIgnoringCase("\"id\" :")
            .containsIgnoringCase("\"version\" :")
            .containsIgnoringCase("\"schema_type\" :")
            .containsIgnoringCase("\"schema\" :")
            .containsIgnoringCase("\"references\" :")
    }

    @Test
    fun `Should download referenced schemas in the specified output dir`() {
        // given
        val testSubject = "test"
        val testLibSubject = "test_lib"
        val outputDir = "src/main/avro/external"

        val registryClient = MockSchemaRegistryClient(listOf(AvroSchemaProvider()))

        registryClient.register(
            testLibSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test_lib",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        registryClient.register(
            testSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test",
                    "fields": [
                        { "name": "name", "type": "test_lib" }
                    ]
                }""",
                listOf(
                    SchemaReference(
                        "name",
                        testLibSubject,
                        1
                    )
                )
            ).get()
        )

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(DownloadSubject(testSubject, outputDir, downloadReferences = true)),
            MetadataExtension()
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isTrue

        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test_lib.avsc").exists()).isTrue
        // Would be cleaner to use a JSON assertion library but I am not sure this is really required for now
    }

    @Test
    fun `Should download referenced schemas recursively`() {
        // given
        val testSubject = "test"
        val testLibSubject = "test_lib"
        val testLibDependencySubject = "test_lib_dependency"
        val outputDir = "src/main/avro/external"

        val registryClient = MockSchemaRegistryClient(listOf(AvroSchemaProvider()))

        registryClient.register(
            testLibDependencySubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test_lib_dependency",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        registryClient.register(
            testLibSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test_lib",
                    "fields": [
                        { "name": "name", "type": "string" },
                        { "name": "dependency", "type": "test_lib_dependency" }
                    ]
                }""",
                listOf(
                    SchemaReference("dependency", testLibDependencySubject, 1)
                )
            ).get()
        )

        registryClient.register(
            testSubject,
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test",
                    "fields": [
                        { "name": "name", "type": "test_lib" }
                    ]
                }""",
                listOf(
                    SchemaReference("name", testLibSubject, 1)
                )
            ).get()
        )

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        val errorCount = DownloadTaskAction(
            registryClient,
            folderRoot,
            arrayListOf(DownloadSubject(testSubject, outputDir, downloadReferences = true)),
            MetadataExtension()
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isTrue

        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test_lib.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test_lib_dependency.avsc").exists()).isTrue
    }

    @Test
    fun `Should format supported schema types when pretty is specified`() {
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
            MetadataExtension(),
            true
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").readText())
            .isEqualTo(
                """
                {
                  "type" : "record",
                  "name" : "test",
                  "fields" : [ {
                    "name" : "name",
                    "type" : "string"
                  } ]
                }
            """.trimIndent() + "\n"
            ) // trimIndent removes trailing newline
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/foo.avsc").exists()).isTrue
        Assertions.assertThat(File(folderRoot, "src/main/avro/external/foo.avsc").readText())
            .isEqualTo(
                """
                {
                  "type" : "record",
                  "name" : "foo",
                  "fields" : [ {
                    "name" : "name",
                    "type" : "string"
                  } ]
                }
            """.trimIndent() + "\n"
            ) // trimIndent removes trailing newline
    }

    @Test
    fun `Should fail silently`() {
        // given
        val testSubject = "test"
        val fooSubject = "foo"
        val outputDir = "src/main/avro/external"

        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))

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
            MetadataExtension()
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(2)
    }

    @Test
    fun `Should fail fast`() {
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

        folderRule.resolve("src/main/avro/external").toFile().mkdir()
        val folderRoot = folderRule.toFile()

        // when
        try {
            DownloadTaskAction(
                registryClient,
                folderRoot,
                arrayListOf(
                    DownloadSubject(fooSubject, outputDir), // Not registered
                    DownloadSubject(testSubject, outputDir),
                ),
                MetadataExtension(),
                failFast = true,
            ).run()
            Assertions.fail("Should have thrown an exception")
        } catch (ex: Exception) {
            Assertions.assertThat(File(folderRoot, "src/main/avro/external/test.avsc").exists()).isFalse // Nothing downloaded
        }
    }
}
