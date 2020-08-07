package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File

abstract class BaseTaskAction(
    val client: SchemaRegistryClient,
    val rootDir: File
) {

    fun parseSchema(schemaPath: String, schemaType: String, dependencies: List<SchemaReference>): ParsedSchema? {
        val schemaString = File(rootDir, schemaPath).readText()
        return client.parseSchema(schemaType, schemaString, dependencies).toNullable()
    }
}
