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
        subjects.forEach { (subject, path, type, remoteDependencies, localDependencies) ->
            try {
                // TODO: Move this to baseTask ?
                //      OK AVRO
                //      This seem to work for JSON (to verify as it shouldn't)
                //      OK for protobuf but need to remove the headers (only keep the messages)
                val schema = localDependencies.values
                    .reversed()
                    .joinToString("\n") { File(rootDir.toURI()).resolve(it).readText() }
                    .plus(File(rootDir.toURI()).resolve(path).readText())
                val parsedSchema = parseSchema(subject, schema, type, remoteDependencies)
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
