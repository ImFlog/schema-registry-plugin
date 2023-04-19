package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.assertj.core.api.Assertions
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

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
}