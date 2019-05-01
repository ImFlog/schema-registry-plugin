package com.github.imflog.schema.registry.compatibility

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.rest.exceptions.Errors
import io.mockk.every
import io.mockk.spyk
import org.apache.avro.Schema
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class CompatibilityTaskActionTest {

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
    fun `Should verify compatibility with no dependencies`() {
        // given
        val parser = Schema.Parser()
        val testSchema =
            parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)
        folderRule.newFolder("src", "main", "avro", "external")

        val subjects = arrayListOf(Triple("test", "src/main/avro/external/test.avsc", emptyList<String>()))
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
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

        // when
        val errorCount = CompatibilityTaskAction(
            registryClient,
            subjects,
            folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should verify compatibility with dependencies`() {
        // given
        val parser = Schema.Parser()
        val testSchema =
            parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)

        folderRule.newFolder("src", "main", "avro", "external")
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": [ "null", "Address" ], "default": null}
             ]
            }
        """.trimIndent()
        )

        File(folderRule.root, "src/main/avro/external/directDependency.avsc").writeText(
            """
            {"type": "record",
             "name": "Address",
             "fields": [
                {"name": "city", "type": "string" },
                {"name": "street", "type": [ "null", "Street" ] }
             ]
            }
        """.trimIndent()
        )

        File(folderRule.root, "src/main/avro/external/undirectDependency.avsc").writeText(
            """
            {"type": "record",
             "name": "Street",
             "fields": [
                {"name": "street", "type": "string", "default": "" }
             ]
            }
        """.trimIndent()
        )


        val subjects = listOf(
            Triple(
                "test",
                "src/main/avro/external/test.avsc",
                listOf(
                    "src/main/avro/external/directDependency.avsc",
                    "src/main/avro/external/undirectDependency.avsc"
                )
            )
        )

        // when
        val errorCount = CompatibilityTaskAction(
            registryClient,
            subjects,
            folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should fail on incompatible schemas`() {
        // given
        val parser = Schema.Parser()
        val testSchema =
            parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)
        folderRule.newFolder("src", "main", "avro", "external")

        val subjects = arrayListOf(Triple("test", "src/main/avro/external/test.avsc", emptyList<String>()))
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "boolean" }
             ]
            }
        """.trimIndent()
        )

        // when
        val errorCount = CompatibilityTaskAction(
            registryClient,
            subjects,
            folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(1)
    }

    @Test
    internal fun `Should succeed if schema does not exist`() {
        // Given

        val spySchemaRegistry = spyk<MockSchemaRegistryClient>()
        every {
            spySchemaRegistry.testCompatibility(any(), any())
        } throws RestClientException("Subject not found", 404, Errors.SUBJECT_NOT_FOUND_ERROR_CODE)

        folderRule.newFolder("src", "main", "avro", "external")

        val subjects = arrayListOf(Triple("test", "src/main/avro/external/test.avsc", emptyList<String>()))
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "boolean" }
             ]
            }
        """.trimIndent()
        )

        // when
        val errorCount = CompatibilityTaskAction(
            spySchemaRegistry,
            subjects,
            folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }
}
