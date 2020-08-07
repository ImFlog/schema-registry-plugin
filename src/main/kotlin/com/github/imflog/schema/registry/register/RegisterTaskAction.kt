package com.github.imflog.schema.registry.register

import com.github.imflog.schema.registry.BaseTaskAction
import com.github.imflog.schema.registry.toNullable
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.gradle.api.logging.Logging
import java.io.File


class RegisterTaskAction(
    client: SchemaRegistryClient,
    rootDir: File,
    private val subjects: List<RegisterSubject>
) : BaseTaskAction(client, rootDir) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { (subject, path, type, dependencies) ->
            try {
                registerSchema(subject, path, type, dependencies)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun registerSchema(subject: String, path: String, type: String, dependencies: List<SchemaReference>) {
        // TODO: Error handling
        val parsedSchema = parseSchema(path, type, dependencies) ?: throw Exception("Could not parse schema")
        logger.debug("Calling register ($subject, $path)")
        client.register(subject, parsedSchema)
    }
}
