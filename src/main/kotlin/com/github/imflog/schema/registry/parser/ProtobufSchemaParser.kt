package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.internal.parser.ProtoParser
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
        localReferences.map { reference ->
            val proto = ProtoParser.parse(
                Location.get(rootDir.resolve(reference.path).absolutePath),
                reference.content(rootDir)
            )
            proto.types // See ProtobufSchema.toMessage ...
        }

        throw NotImplementedError("Local reference is not available for Protobuf yet")
    }
}