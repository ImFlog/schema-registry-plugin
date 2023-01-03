package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import java.io.File

class ProtobufSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.PROTOBUF

    override fun resolveLocalReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>
    ): String {
        throw NotImplementedError("Local reference is not available for Protobuf yet")
    }
}