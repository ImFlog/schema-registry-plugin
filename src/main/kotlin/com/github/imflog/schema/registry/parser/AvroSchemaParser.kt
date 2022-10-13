package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.apache.avro.Schema
import java.io.File

class AvroSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.AVRO

    // TODO: We can probably only make localReference abstract ?
    override fun parseSchemaFromFile(
        subject: String,
        schemaPath: String,
        remoteReferences: List<SchemaReference>,
        localReferences: List<LocalReference>
    ): ParsedSchema {
        val schemaContent = rootDir.resolve(schemaPath).readText()
        val parsedLocalSchemaString = if (localReferences.isNotEmpty()) {
            resolveLocalReferences(localReferences, schemaContent).toString()
        } else schemaContent

        return client
            .parseSchema(schemaType.registryType, parsedLocalSchemaString, remoteReferences)
            .orElseThrow { SchemaParsingException(subject, schemaType) }
    }

    private fun resolveLocalReferences(
        localReferences: List<LocalReference>,
        schemaContent: String
    ): Schema {
        val parser = Schema.Parser()
        localReferences.map { reference -> reference.content(rootDir) }.reversed().forEach { parser.parse(it) }
        return parser.parse(schemaContent)
    }
}