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
    fun parseSchema(
        subject: String,
        schemaPath: String,
        schemaType: String,
        dependencies: List<SchemaReference>
    ): ParsedSchema {
        val schemaContent = File(rootDir.toURI()).resolve(schemaPath).readText()
        // TODO : This is ugly
        val modifiedForLocalDependencies = dependencies
            .map {
                if (it.version == -2) SchemaReference(
                    File(rootDir.toURI()).resolve(it.name).absolutePath,
                    it.subject,
                    it.version
                ) else it
            }
        return client.parseSchema(schemaType, schemaContent, modifiedForLocalDependencies).orElseThrow {
            SchemaParsingException(subject, schemaType)
        }
    }

    /**
     * Utility method that checks if the quiet logging is activated before logging.
     * This is needed because we cannot set a log level per task.
     * See https://github.com/gradle/gradle/issues/1010
     */
    fun Logger.infoIfNotQuiet(message: String) {
        if (!quietLogging) this.info(message)
    }
}
