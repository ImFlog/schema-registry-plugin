package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.assertj.core.api.Assertions
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonSchemaParserTest {

    private val schemaRegistryClient = MockSchemaRegistryClient()
    @TempDir
    lateinit var folderRule: Path

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
        private const val USER_SCHEMA = """{
            "${"$"}schema": "http://json-schema.org/draft-07/schema#",
            "${"$"}id": "$USER_REFERENCE_NAME",
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "address": {"${"$"}ref": "$ADDRESS_REFERENCE_NAME"}
            },
            "additionalProperties": false
        }"""
    }

    @Test
    fun `Should format local references correctly`() {
        // Given
        val parser = JsonSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference()
        val aUserSchemaFile = givenASchemaFile()

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            USER_REFERENCE_NAME,
            aUserSchemaFile.path,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldBeAppended(resolvedSchema)
    }

    @Test
    fun `Should resolve references with custom root directory`() {
        // Given
        val customRoot = folderRule.resolve("src/main/json")
        customRoot.toFile().mkdirs()
        val parser = JsonSchemaParser(schemaRegistryClient, customRoot.toFile())

        val addressFile = customRoot.resolve("Address.json").toFile()
        addressFile.writeText(
            """{
            "${"$"}schema": "http://json-schema.org/draft-07/schema#",
            "${"$"}id": "Address",
            "type": "object",
            "properties": {
                "street": {"type": "string"}
            }
        }"""
        )

        val userFile = customRoot.resolve("User.json").toFile()
        userFile.writeText(
            """{
            "${"$"}schema": "http://json-schema.org/draft-07/schema#",
            "${"$"}id": "User",
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "address": {"${"$"}ref": "Address"}
            }
        }"""
        )

        // The path in LocalReference is relative to the rootDir.
        val localRef = LocalReference("Address", "Address.json")

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "User",
            "User.json",
            listOf(localRef)
        )

        // Then
        Assertions.assertThat(JSONObject(resolvedSchema)).isNotNull
        Assertions.assertThat(resolvedSchema).contains("street")
    }

    private fun givenALocalReference(): LocalReference {
        val addressLocalFile = folderRule.resolve("Address.json").toFile()
        addressLocalFile.writeText(ADDRESS_SCHEMA)
        return LocalReference(ADDRESS_REFERENCE_NAME, addressLocalFile.path)
    }

    private fun givenASchemaFile(): File {
        val userLocalFile = folderRule.resolve("User.json").toFile()
        userLocalFile.writeText(USER_SCHEMA)
        return userLocalFile
    }

    private fun localSchemaShouldBeAppended(resolvedSchema: String) {
        Assertions
            .assertThat(JSONObject(resolvedSchema).getJSONArray("${"$"}defs").get(0).toString())
            .isEqualTo(JSONObject(mapOf(ADDRESS_REFERENCE_NAME to JSONObject(ADDRESS_SCHEMA))).toString())
    }
}
