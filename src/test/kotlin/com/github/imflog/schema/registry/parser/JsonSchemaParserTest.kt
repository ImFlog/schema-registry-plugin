package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.json.JSONObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonSchemaParserTest {

    private val schemaRegistryClient = MockSchemaRegistryClient()
    private val folderRule: TemporaryFolder = TemporaryFolder().apply { create() }
    private val parser = JsonSchemaParser(
        schemaRegistryClient,
        folderRule.root
    )

    companion object {
        private const val ADDRESS_REFERENCE_NAME = "Address"
        private const val USER_REFERENCE_NAME = "User"
        private const val ADDRESS_SCHEMA = """{
            "${"$"}schema": "http://json-schema.org/draft-07/schema#",
            "${"$"}id": "$ADDRESS_REFERENCE_NAME",
            "type": "object",
            "properties": {
                "street": {"type": "string"}
            },
            "additionalProperties": false
        }"""
    }

    @AfterAll
    fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `Should format local references correctly`() {
        // Given
        val aLocalReference = givenALocalReference()
        val aSchema = givenASchema()

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            USER_REFERENCE_NAME,
            aSchema,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldBeAppended(resolvedSchema)
    }

    private fun givenASchema(): String = """
        {
            "${"$"}schema": "http://json-schema.org/draft-07/schema#",
            "${"$"}id": "$USER_REFERENCE_NAME",
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "address": {"${"$"}ref": "$ADDRESS_REFERENCE_NAME"}
            },
            "additionalProperties": false
        }""".trimIndent()

    private fun givenALocalReference(): LocalReference {
        val addressLocalFile = folderRule.root.resolve("Address.json")
        addressLocalFile.writeText(ADDRESS_SCHEMA)
        return LocalReference(ADDRESS_REFERENCE_NAME, addressLocalFile.path)
    }

    private fun localSchemaShouldBeAppended(resolvedSchema: String) {
        Assertions
            .assertThat(JSONObject(resolvedSchema).getJSONArray("${"$"}defs"))
            .singleElement()
            .usingRecursiveComparison() // To check the content of the JSON Objects instead of the equal method
            .isEqualTo(JSONObject(mapOf(ADDRESS_REFERENCE_NAME to JSONObject(ADDRESS_SCHEMA))))
    }
}