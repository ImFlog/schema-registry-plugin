package com.github.imflog.schema.registry.tasks

import com.github.imflog.schema.registry.SchemaParsingException
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import java.io.File
import org.apache.avro.Schema
import org.slf4j.Logger

abstract class BaseTaskAction(
    val client: SchemaRegistryClient,
    val rootDir: File,
    private val quietLogging: Boolean
) {

    fun parseSchema(
        subject: String,
        schemaPath: String,
        schemaType: String,
        references: List<SchemaReference>,
        localReferences: Map<String, String>
    ): ParsedSchema =
        if (localReferences.isEmpty()) {
            parseSchemaWithoutLocalDependencies(schemaPath, schemaType, references, subject)
        } else {
            when (schemaType) {
                AvroSchema.TYPE -> parseAvroSchemaWithLocalReferencies(subject, schemaPath, references, localReferences)
                ProtobufSchema.TYPE,
                JsonSchema.TYPE -> throw NotImplementedError("LocalDependencies resolver is not yet available for $schemaType")
                else -> throw NotImplementedError("LocalDependencies resolver is not yet available for $schemaType")
            }
        }

    private fun parseSchemaWithoutLocalDependencies(
        schemaPath: String,
        schemaType: String,
        dependencies: List<SchemaReference>,
        subject: String
    ): ParsedSchema {
        val schemaContent = File(rootDir.toURI()).resolve(schemaPath).readText()
        return client.parseSchema(schemaType, schemaContent, dependencies)
            .orElseThrow { SchemaParsingException(subject, schemaType) }
    }

    /**
     * Utility method that checks if the quiet logging is activated before logging.
     * This is needed because we cannot set a log level per task.
     * See https://github.com/gradle/gradle/issues/1010
     */
    fun Logger.infoIfNotQuiet(message: String) {
        if (!quietLogging) this.info(message)
    }

    private fun parseAvroSchemaWithLocalReferencies(
        subject: String,
        schemaPath: String,
        dependencies: List<SchemaReference>,
        localDependencies: Map<String, String>
    ): ParsedSchema {
        val parser = Schema.Parser()
        localDependencies.mapValues { File(rootDir.toURI()).resolve(it.value) }.entries.reversed()
            .forEach { parser.parse(it.value.readText()) }
        val parsedLocalSchema = parser.parse(File(rootDir.toURI()).resolve(schemaPath).readText())
        return client.parseSchema(AvroSchema.TYPE, parsedLocalSchema.toString(), dependencies)
            .orElseThrow { SchemaParsingException(subject, AvroSchema.TYPE) }
    }
}
