package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvroSchemaParserTest {

    private val schemaRegistryClient = MockSchemaRegistryClient()
    private val testFilesPath = "${Paths.get("").toAbsolutePath()}/src/test/resources/"

    @TempDir
    lateinit var folderRule: Path

    companion object {
        private const val ADDRESS_REFERENCE_NAME = "Address"
        private const val USER_REFERENCE_NAME = "User"
        private const val ADDRESS_SCHEMA = """{
             "type": "record",
             "name": "$ADDRESS_REFERENCE_NAME",
             "fields": [
                {"name": "street", "type": "string" }
             ]
        }"""
        private const val USER_SCHEMA = """{
             "type": "record",
             "name": "$USER_REFERENCE_NAME",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": "$ADDRESS_REFERENCE_NAME"}
             ]
        }"""

    }

    @Test
    fun `Should format local references correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference()

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            USER_REFERENCE_NAME,
            USER_SCHEMA,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldBeAppended(resolvedSchema)
    }

    private fun givenALocalReference(): LocalReference {
        val addressLocalFile = folderRule.resolve("Address.avsc").toFile()
        addressLocalFile.writeText(ADDRESS_SCHEMA)
        return LocalReference(ADDRESS_REFERENCE_NAME, addressLocalFile.path)
    }

    private fun localSchemaShouldBeAppended(resolvedSchema: String) {
        Assertions
            .assertThat(
                JSONObject(resolvedSchema)
                    .getJSONArray("fields")
                    .getJSONObject(1)
                    .getJSONObject("type")
            )
            .usingRecursiveComparison() // To check the content of the JSON Objects instead of the equal method
            .isEqualTo(JSONObject(ADDRESS_SCHEMA))
    }

    @Test
    fun `Should resolve duplicated references with same namespace correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val reference = LocalReference("B", "${testFilesPath}testType.avsc")
        val schema = File("${testFilesPath}testSubject.avsc").readText()
        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "test",
            schema,
            listOf(reference)
        )
        // Then
        val resolved = JSONObject(resolvedSchema).toString()

        @Language("JSON")
        val expected = """{
          "name": "A",
          "namespace": "com.mycompany",
          "type": "record",
          "fields": [
            {
              "name": "nested",
              "type": {
                "name":"B",
                "type": "enum",
                "symbols": ["X1", "X2"]
              }
            },
            {
              "name": "nested1",
              "type": "B"
            }
          ]
        }"""

        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }

    @Test
    fun `Should resolve complex nested array example correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))

        val schema = File("${testFilesPath}ParentSubject.avsc")
            .readText()

        // com.github.imflog.avro.SchemaParseException: Undefined name: "NestedNestedType"
        assertDoesNotThrow {
            val resolvedSchema = parser.resolveLocalReferences(
                "test",
                schema,
                listOf(
                    LocalReference("NestedArrayType", "${testFilesPath}NestedArrayType.avsc"),
                    LocalReference("NestedNestedType", "${testFilesPath}NestedNestedType.avsc"),
                )
            )
        }
    }

    @Test
    fun `Should resolve complex nested record example correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))

        val schema = File("${testFilesPath}ParentSubject.avsc")
            .readText()

        // com.github.imflog.avro.SchemaParseException: Can't redefine: com.test.company.NestedNestedType
        assertDoesNotThrow {
            val resolvedSchema = parser.resolveLocalReferences(
                "test",
                schema,
                listOf(
                    LocalReference("NestedType", "${testFilesPath}NestedType.avsc"),
                    LocalReference("NestedNestedType", "${testFilesPath}NestedNestedType.avsc"),
                )
            )
        }
    }

    @Test
    fun `Should resolve duplicated array references correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val reference = LocalReference("B", "${testFilesPath}testType.avsc")
        val schema = File("${testFilesPath}testSubjectWithArrayReference.avsc")
            .readText()
        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "test",
            schema,
            listOf(reference)
        )
        // Then
        val resolved = JSONObject(resolvedSchema).toString()

        @Language("JSON")
        val expected = """{
           "name": "A",
           "namespace": "com.mycompany",
           "type": "record",
           "fields": [
             {
               "name": "nested",
               "type": {
                "type": "array",
                "items": {
                        "name":"B",
                        "type":"enum",
                        "symbols": ["X1","X2"]
                    }
                }
            },
            {
                "name":"nested1",
                "type":{
                    "type":"array",
                    "items":"B"
                }
            }
          ]
        }"""

        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }

    @Test
    fun `Should parse with unknown remote references correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val schema = File("${testFilesPath}testSubject.avsc")
            .readText()
        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "test",
            schema,
            listOf()
        )
        // Then
        val resolved = JSONObject(resolvedSchema).toString()

        @Language("JSON")
        val expected = """{
          "type": "record",
          "name": "A",
          "namespace": "com.mycompany",
          "fields": [
            {
              "name": "nested",
              "type": "B"
            },
            {
              "name": "nested1",
              "type": "B"
            }
          ]
        }"""

        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }
}