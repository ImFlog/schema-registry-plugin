package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.tasks.BaseTaskAction
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.gradle.api.logging.Logging
import java.io.File


class RegisterTaskAction(
    client: SchemaRegistryClient,
    rootDir: File,
    private val subjects: List<RegisterSubject>,
    quietLogging: Boolean
) : BaseTaskAction(client, rootDir, quietLogging) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { (subject, path, type, dependencies) ->
            try {
                val parsedSchema = parseSchemaFromFile(path, type, dependencies)
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
