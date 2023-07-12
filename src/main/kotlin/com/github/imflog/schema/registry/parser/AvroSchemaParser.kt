package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import com.github.imflog.avro.Schema.Parser
import java.io.File

class AvroSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.AVRO

    override fun resolveLocalReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>
    ): String {
        val parser = Parser()
            .setValidate(false)
            .setValidateDefaults(false)
            .setValidateUnknownTypes(false)
        localReferences
            .reversed()
            .map { reference -> reference.content(rootDir) }
            .forEach { parser.parse(it) }
        return parser.parse(schemaContent).toString()
    }
}