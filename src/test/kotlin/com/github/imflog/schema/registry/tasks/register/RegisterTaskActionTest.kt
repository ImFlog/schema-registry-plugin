package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.Subject
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RegisterTaskActionTest {
    @TempDir
    lateinit var folderRule: Path

    @BeforeEach
    fun init() {
        Files.createDirectories(folderRule.resolve("src/main/avro/external/"))
        Files.createFile(folderRule.resolve("src/main/avro/external/test.avsc"))
    }

    @Test
    fun `Should register new schema`() {
        // given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        File(folderRule.toFile(), "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "newField", "type": "string", "default": ""}
             ]
            }
        """.trimIndent()
        )

        val subjects = listOf(
            Subject("test", "src/main/avro/external/test.avsc", "AVRO")
        )

        // when
        val errorCount = RegisterTaskAction(
            registryClient,
            folderRule.toFile(),
            subjects,
            null
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should update version for same schema`() {
        // given
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

        File(folderRule.toFile(), "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "newField", "type": "string", "default": ""}
             ]
            }
        """.trimIndent()
        )

        val subjects = listOf(
            Subject(
                "test",
                "src/main/avro/external/test.avsc",
                "AVRO"
            )
        )

        // when
        val errorCount = RegisterTaskAction(
            registryClient,
            folderRule.toFile(),
            subjects,
            null
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(registryClient.getLatestSchemaMetadata("test").version)
            .isEqualTo(2)
    }

    @Test
    fun `Should register schema with references`() {
        // given
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

        // Register dependency
        registryClient.register(
            "Street",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Street",
                    "fields": [
                        {"name": "street", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        registryClient.register(
            "Address",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Address",
                    "fields": [
                        {"name": "city", "type": "string" },
                        {"name": "street", "type": "Street" }
                    ]
                }""",
                listOf(SchemaReference("Street", "Street", 1))
            ).get()
        )

        File(folderRule.toFile(), "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": "Address"}
             ]
            }
        """.trimIndent()
        )


        val subjects = listOf(
            Subject(
                "test",
                "src/main/avro/external/test.avsc",
                "AVRO"
            )
                .addReference("Address", "Address", 1)
                .addReference("Street", "Street", 1)
        )

        // when
        val errorCount = RegisterTaskAction(
            registryClient,
            folderRule.toFile(),
            subjects,
            null
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(registryClient.getLatestSchemaMetadata("test")).isNotNull
    }

    @Test
    fun `Should register schema with latest remote version of references`() {
        // given
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

        // Register dependency
        registryClient.register(
            "Street",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Street",
                    "fields": [
                        {"name": "street", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        registryClient.register(
            "Address",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Address",
                    "fields": [
                        {"name": "city", "type": "string" },
                        {"name": "street", "type": "Street" }
                    ]
                }""",
                listOf(SchemaReference("Street", "Street", 1))
            ).get()
        )

        File(folderRule.toFile(), "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": "Address"}
             ]
            }
        """.trimIndent()
        )


        val subjects = listOf(
            Subject(
                "test",
                "src/main/avro/external/test.avsc",
                "AVRO"
            )
                .addReference("Address", "Address")
                .addReference("Street", "Street", -1)
        )

        // when
        val errorCount = RegisterTaskAction(
            registryClient,
            folderRule.toFile(),
            subjects,
            null
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(registryClient.getLatestSchemaMetadata("test")).isNotNull
    }

    @Test
    fun `Should register schema and write the output to a file`() {
        // given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val resultFolder = Files.createDirectories(folderRule.resolve("results/avro")).toFile()
        File(folderRule.toFile(), "src/main/avro/external/test.avsc")
            .writeText(
                """
                {"type": "record",
                 "name": "test",
                 "fields": [
                    {"name": "name", "type": "string" },
                    {"name": "newField", "type": "string", "default": ""}
                 ]
                }
            """
            )
        File(folderRule.toFile(), "src/main/avro/external/test_2.avsc")
            .writeText(
                """
                {"type": "record",
                 "name": "test_2",
                 "fields": [
                    {"name": "name", "type": "string" },
                    {"name": "newField", "type": "string", "default": ""}
                 ]
                }
            """
            )

        val subjects = listOf(
            Subject("test", "src/main/avro/external/test.avsc", "AVRO"),
            Subject("test_2", "src/main/avro/external/test_2.avsc", "AVRO"),
        )

        // when
        RegisterTaskAction(
            registryClient,
            folderRule.toFile(),
            subjects,
            resultFolder.path
        ).run()

        // then
        val expectedOutputFile = resultFolder.resolve("registered.csv")
        Assertions.assertThat(expectedOutputFile).exists()
            .hasContent(
                """subject, path, id
               |test, src/main/avro/external/test.avsc, 1
               |test_2, src/main/avro/external/test_2.avsc, 2
               |""".trimMargin()
            )
    }

    @Test
    fun `Should register schema with duplicated local references`() {
        // Given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
        val schemaPath = "$projectDirAbsolutePath/src/test/resources/"
        val subjects = listOf(
            Subject("test", "${schemaPath}testSubject.avsc", "AVRO")
                .addLocalReference("B", "${schemaPath}testType.avsc")
        )

        // When
        val errorCount = RegisterTaskAction(
            registryClient,
            folderRule.root.toFile(),
            subjects,
            null
        ).run()

        // Then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should register schema with ruleSet`() {
        // Given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
        val schemaPath = "$projectDirAbsolutePath/src/test/resources/"
        val subjects = listOf(
            Subject("test", "${schemaPath}testSubjectWithTag.avsc", "AVRO")
                .setRuleSet("${schemaPath}testRuleSet.json")
        )

        // When
        val errorCount = RegisterTaskAction(
            registryClient,
            folderRule.root.toFile(),
            subjects,
            null
        ).run()

        // Then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(registryClient.getLatestSchemaMetadata("test").ruleSet).isNotNull
    }

    @Test
    fun `Should register schema with metadata`() {
        // Given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
        val schemaPath = "$projectDirAbsolutePath/src/test/resources/"
        val subjects = listOf(
            Subject("test", "${schemaPath}testSimpleSubject.avsc", "AVRO")
                .setMetadata("${schemaPath}testMetadata.json")
        )

        // When
        val errorCount = RegisterTaskAction(
            registryClient,
            folderRule.root.toFile(),
            subjects,
            null
        ).run()

        // Then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(registryClient.getLatestSchemaMetadata("test").metadata).isNotNull
    }

    @Test
    fun `Should register normalized schema`() {
        // Given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val resultFolder = Files.createDirectories(folderRule.resolve("results/protobuf")).toFile()
        File(folderRule.toFile(), "src/main/avro/external/test.proto")
            .writeText(
                """
            syntax = "proto3";
            
            option java_package = "com.example.proto.v1";
            package foo.v1;
               
            option java_outer_classname = "FooProto";
            option java_multiple_files = true;
            
            message Foo {
              string bar = 1;
            }
            """
            )

        val subjects = listOf(
            Subject("test", "src/main/avro/external/test.proto", "PROTOBUF").setNormalized(true),
        )

        // when
        RegisterTaskAction(
            registryClient,
            folderRule.toFile(),
            subjects,
            resultFolder.path
        ).run()

        // then
        Assertions.assertThat(registryClient.getLatestSchemaMetadata("test").schema)
            .isEqualTo("syntax = \"proto3\";\n" +
                    "package foo.v1;\n" +
                    "\n" +
                    "option java_multiple_files = true;\n" +
                    "option java_outer_classname = \"FooProto\";\n" +
                    "option java_package = \"com.example.proto.v1\";\n" +
                    "\n" +
                    "message Foo {\n" +
                    "  string bar = 1;\n" +
                    "}\n"
        )
    }
}
