package com.github.imflog.schema.registry.tasks

import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchema
import java.io.File
import org.apache.avro.Schema
import org.slf4j.Logger

abstract class BaseTaskAction(
    val client: SchemaRegistryClient,
    val rootDir: File,
    private val quietLogging: Boolean
) {

    fun parseSchemaFromFile(
        subject: String,
        schemaPath: String,
        schemaType: SchemaType,
        references: List<SchemaReference>,
        localReferences: Map<String, String>
    ): ParsedSchema {
        val schemaContent = rootDir.resolve(schemaPath).readText()
        return if (localReferences.isEmpty()) {
            parseSchemaWithRemoteReferences(subject, schemaType, schemaContent, references)
        } else {
            parseSchemaWithLocalReferences(subject, schemaType, schemaContent, localReferences)
        }
    }

    fun parseSchemaWithRemoteReferences(
        subject: String,
        schemaType: SchemaType,
        schemaContent: String,
        references: List<SchemaReference>,
    ): ParsedSchema = client
        .parseSchema(schemaType.registryType, schemaContent, references)
        .orElseThrow { SchemaParsingException(subject, schemaType) }

    /**
     * Utility method that checks if the quiet logging is activated before logging.
     * This is needed because we cannot set a log level per task.
     * See https://github.com/gradle/gradle/issues/1010
     */
    fun Logger.infoIfNotQuiet(message: String) {
        if (!quietLogging) this.info(message)
    }

    private fun parseSchemaWithLocalReferences(
        subject: String,
        schemaType: SchemaType,
        schemaContent: String,
        localReferences: Map<String, String>
    ): ParsedSchema = when (schemaType) {
        SchemaType.AVRO -> parseAvroSchemaWithLocalReferences(
            subject,
            schemaContent,
            localReferences
        )

        SchemaType.JSON -> parseJsonSchemaWithLocalReferences(
            subject,
            schemaContent,
            localReferences
        )

        SchemaType.PROTOBUF -> throw NotImplementedError(
            "LocalReferences resolver is not yet available for $schemaType"
        )
    }

    private fun parseAvroSchemaWithLocalReferences(
        subject: String,
        schemaString: String,
        localReferences: Map<String, String>
    ): ParsedSchema {
        val parser = Schema.Parser()
        localReferences.mapValues { rootDir.resolve(it.value) }.entries.reversed()
            .forEach { parser.parse(it.value.readText()) }
        val parsedLocalSchema = parser.parse(schemaString)
        return client.parseSchema(AvroSchema.TYPE, parsedLocalSchema.toString(), emptyList())
            .orElseThrow { SchemaParsingException(subject, SchemaType.AVRO) }
    }

    private fun parseJsonSchemaWithLocalReferences(
        subject: String,
        schemaContent: String,
        localReferences: Map<String, String>
    ): ParsedSchema {
        val schema = JsonSchema(schemaContent, emptyList(), localReferences, null)
        return client.parseSchema(JsonSchema.TYPE, schema.toString(), emptyList())
            .orElseThrow { SchemaParsingException(subject, SchemaType.JSON) }
    }
}
