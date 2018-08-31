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

        val subjects = arrayListOf(Pair("test", listOf("src/main/avro/external/test.avsc")))
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

        val subjects = arrayListOf(Pair("test", listOf("src/main/avro/external/test.avsc")))
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
}