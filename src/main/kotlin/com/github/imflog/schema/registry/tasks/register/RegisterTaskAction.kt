package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.tasks.BaseTaskAction
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import java.io.File
import org.gradle.api.logging.Logging


class RegisterTaskAction(
    client: SchemaRegistryClient,
    rootDir: File,
    private val subjects: List<RegisterSubject>,
    quietLogging: Boolean
) : BaseTaskAction(client, rootDir, quietLogging) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { (subject, path, type, references, localReferences) ->
            try {
                val parsedSchema = parseSchemaFromFile(subject, path, type, references, localReferences)
                logger.infoIfNotQuiet("Registering $subject (from $path)")
                client.register(subject, parsedSchema)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                errorCount++
            }
        }
        return errorCount
    }
}
