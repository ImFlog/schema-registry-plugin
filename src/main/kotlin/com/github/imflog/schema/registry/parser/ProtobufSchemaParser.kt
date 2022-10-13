package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File

class ProtobufSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.PROTOBUF

    override fun parseSchemaFromFile(
        subject: String,
        schemaPath: String,
        remoteReferences: List<SchemaReference>,
        localReferences: List<LocalReference>
    ): ParsedSchema {
        val schemaContent = rootDir.resolve(schemaPath).readText()
        return client
            .parseSchema(schemaType.registryType, schemaContent, remoteReferences)
            .orElseThrow { SchemaParsingException(subject, schemaType) }
    }
}