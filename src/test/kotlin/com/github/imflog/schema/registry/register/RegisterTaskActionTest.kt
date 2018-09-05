package com.github.imflog.schema.registry.register

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.apache.avro.Schema
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class RegisterTaskActionTest {
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
    fun `Should register new schema`() {
        // given
        val registryClient = MockSchemaRegistryClient()
        folderRule.newFolder("src", "main", "avro", "external")
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText("""
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "newField", "type": "string", "default": ""}
             ]
            }
        """.trimIndent())

        val subjects = listOf(
                Triple(
                        "test",
                        "src/main/avro/external/test.avsc",
                        listOf<String>()
                ))

        // when
        val errorCount = RegisterTaskAction(
                registryClient,
                subjects,
                folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should update version for same schema`() {
        // given
        val parser = Schema.Parser()
        val testSchema = parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)

        folderRule.newFolder("src", "main", "avro", "external")
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText("""
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "newField", "type": "string", "default": ""}
             ]
            }
        """.trimIndent())

        val subjects = listOf(Triple(
                "test",
                "src/main/avro/external/test.avsc",
                listOf<String>()
        ))

        // when
        val errorCount = RegisterTaskAction(
                registryClient,
                subjects,
                folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(registryClient.getLatestSchemaMetadata("test").version)
                .isEqualTo(2)
    }

    @Test
    internal fun `Should register schema with dependencies in another file`() {
        // given
        val parser = Schema.Parser()
        val testSchema = parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)

        folderRule.newFolder("src", "main", "avro", "external")
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText("""
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": "Address"}
             ]
            }
        """.trimIndent())

        File(folderRule.root, "src/main/avro/external/directDependency.avsc").writeText("""
            {"type": "record",
             "name": "Address",
             "fields": [
                {"name": "city", "type": "string" },
                {"name": "street", "type": "Street" }
             ]
            }
        """.trimIndent())

        File(folderRule.root, "src/main/avro/external/undirectDependency.avsc").writeText("""
            {"type": "record",
             "name": "Street",
             "fields": [
                {"name": "street", "type": "string" }
             ]
            }
        """.trimIndent())


        val subjects = listOf(
                Triple(
                        "test",
                        "src/main/avro/external/test.avsc",
                        listOf(
                                "src/main/avro/external/directDependency.avsc",
                                "src/main/avro/external/undirectDependency.avsc"))
        )

        // when
        val errorCount = RegisterTaskAction(
                registryClient,
                subjects,
                folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }
}