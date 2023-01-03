package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File

abstract class SchemaParser(
    protected val client: SchemaRegistryClient,
    protected val rootDir: File
) {
    abstract val schemaType: SchemaType

    companion object {
        /**
         * This is like a factory for our local parsers.
         * We can afford to recreate the parser each time as it's not a long-running processes.
         */
        fun provide(schemaType: SchemaType, client: SchemaRegistryClient, rootDir: File): SchemaParser =
            when (schemaType) {
                SchemaType.AVRO -> AvroSchemaParser(client, rootDir)
                SchemaType.JSON -> JsonSchemaParser(client, rootDir)
                SchemaType.PROTOBUF -> ProtobufSchemaParser(client, rootDir)
            }
    }

    @Throws(SchemaParsingException::class, NotImplementedError::class)
    fun parseSchemaFromFile(
        subject: String,
        schemaPath: String,
        remoteReferences: List<SchemaReference>,
        localReferences: List<LocalReference>,
    ): ParsedSchema {
        val schemaContent = rootDir.resolve(schemaPath).readText()
        val parsedLocalSchemaString = if (localReferences.isNotEmpty()) {
            resolveLocalAndRemoteReferences(subject, schemaContent, localReferences,remoteReferences)
        } else schemaContent

        return client
            .parseSchema(schemaType.registryType, parsedLocalSchemaString, if (localReferences.isNotEmpty()) listOf() else remoteReferences)
            .orElseThrow { SchemaParsingException(subject, schemaType) }
    }

    abstract fun resolveLocalAndRemoteReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>,
        remoteReferences: List<SchemaReference>
    ): String

}

