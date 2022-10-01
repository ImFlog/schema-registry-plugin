package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.tasks.BaseTaskAction
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.gradle.api.logging.Logging
import java.io.File


class RegisterTaskAction(
    client: SchemaRegistryClient,
    rootDir: File,
    private val subjects: List<RegisterSubject>,
    quietLogging: Boolean,
    outputDir: File?
) : BaseTaskAction(client, rootDir, quietLogging) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)
    private val outputFile = outputDir?.resolve("registered.csv")

    fun run(): Int {
        var errorCount = 0
        writeOutputFileHeader()
        subjects.forEach { (subject, path, type, references, localReferences) ->
            try {
                val schemaId = registerSchema(subject, path, type, references, localReferences)
                writeRegisteredSchemaOutput(subject, path, schemaId)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun registerSchema(
        subject: String,
        path: String,
        type: SchemaType,
        references: MutableList<SchemaReference>,
        localReferences: MutableMap<String, String>
    ): Int {
        val parsedSchema = parseSchemaFromFile(subject, path, type, references, localReferences)
        logger.infoIfNotQuiet("Registering $subject (from $path)")
        val schemaId = client.register(subject, parsedSchema)
        logger.infoIfNotQuiet("$subject (from $path) has been registered with id $schemaId")
        return schemaId
    }

    private fun writeOutputFileHeader() {
        if (subjects.isNotEmpty() && outputFile != null) {
            outputFile.writeText("subject, path, id\n")
        }
    }

    private fun writeRegisteredSchemaOutput(subject: String, path: String, schemaId: Int) {
        outputFile?.appendText("$subject, $path, $schemaId\n")
    }
}
