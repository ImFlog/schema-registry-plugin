package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.Subject
import com.github.imflog.schema.registry.tasks.register.RegisterTaskAction
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.assertj.core.api.Assertions
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvroSchemaParserTest {

    private val schemaRegistryClient = MockSchemaRegistryClient()

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
        val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
        val testFilesPath = "$projectDirAbsolutePath/src/test/resources/"
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val reference = LocalReference("B", "${testFilesPath}B.avsc")
        val schema = File("${testFilesPath}A.avsc").readText()
        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "test",
            schema,
            listOf(reference)
        )
        // Then
        val resolved = JSONObject(resolvedSchema).toString()
        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(
                """
                {
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
                }
                """
            ).toString()
        )
    }

    @Test
    fun `Should resolve duplicated references with different namespace correctly`() {
        // Given
        val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
        val testFilesPath = "$projectDirAbsolutePath/src/test/resources/"
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val reference = LocalReference("B", "${testFilesPath}B.avsc")
        val schema = File("${testFilesPath}A.avsc")
            .readText()
            .replace("com.mycompany", "com.mycompany.namespaced")
        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "test",
            schema,
            listOf(reference)
        )
        // Then
        val resolved = JSONObject(resolvedSchema).toString()
        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(
                """
                {
                  "name": "A",
                  "namespace": "com.mycompany.namespaced",
                  "type": "record",
                  "fields": [
                    {
                      "name": "nested",
                      "type": {
                        "name":"B",
                        "namespace": "com.mycompany",
                        "type": "enum",
                        "symbols": ["X1", "X2"]
                      }
                    },
                    {
                      "name": "nested1",
                      "type": "com.mycompany.B"
                    }
                  ]
                }
                """
            ).toString()
        )
    }

}