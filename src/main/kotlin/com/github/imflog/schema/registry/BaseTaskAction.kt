package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File

abstract class BaseTaskAction(
    val client: SchemaRegistryClient,
    val rootDir: File
) {

    fun parseSchemaFromFile(schemaPath: String, schemaType: String, dependencies: List<SchemaReference>): ParsedSchema {
        val schemaString = File(rootDir.toURI()).resolve(schemaPath).readText()
        return parseSchema(schemaPath, schemaString, schemaType, dependencies)
    }

    fun parseSchema(
        subject: String,
        schemaContent: String,
        schemaType: String,
        dependencies: List<SchemaReference>
    ): ParsedSchema =
        client.parseSchema(schemaType, schemaContent, dependencies).orElseThrow {
            SchemaParsingException(subject, schemaType)
        }
}
