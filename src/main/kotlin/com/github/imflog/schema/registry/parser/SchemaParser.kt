package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.LocalReference
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
        fun provide(schemaType: SchemaType, client: SchemaRegistryClient, rootDir: File): SchemaParser =
            when (schemaType) {
                SchemaType.AVRO -> AvroSchemaParser(client, rootDir)
                SchemaType.JSON -> JsonSchemaParser(client, rootDir)
                SchemaType.PROTOBUF -> ProtobufSchemaParser(client, rootDir)
            }
    }

    /**
     * Parses a schema from a file.
     * Default implementation does not resolve the local references.
     * Specific implementation can change based on the different protocols.
     */
    @Throws(SchemaParsingException::class)
    abstract fun parseSchemaFromFile(
        subject: String,
        schemaPath: String,
        remoteReferences: List<SchemaReference>,
        localReferences: List<LocalReference>,
    ): ParsedSchema
}

