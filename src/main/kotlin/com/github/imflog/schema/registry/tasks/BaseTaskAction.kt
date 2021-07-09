package com.github.imflog.schema.registry.tasks

import com.github.imflog.schema.registry.SchemaParsingException
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File
import org.slf4j.Logger

abstract class BaseTaskAction(
    val client: SchemaRegistryClient,
    val rootDir: File,
    private val quietLogging: Boolean
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

    /**
     * Utility method that checks if the quiet logging is activated before logging.
     * This is needed because we cannot setup a log level per task.
     * See https://github.com/gradle/gradle/issues/1010
     */
    fun Logger.infoIfNotQuiet(message: String) {
        if (!quietLogging) this.info(message)
    }
}
