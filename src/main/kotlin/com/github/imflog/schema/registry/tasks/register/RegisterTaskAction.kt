package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.LoggingUtils.infoIfNotQuiet
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.Subject
import com.github.imflog.schema.registry.parser.SchemaParser
import com.github.imflog.schema.registry.toSchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.Metadata
import io.confluent.kafka.schemaregistry.client.rest.entities.RuleSet
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.gradle.api.logging.Logging
import java.io.File


class RegisterTaskAction(
    private val client: SchemaRegistryClient,
    private val rootDir: File,
    private val subjects: List<Subject>,
    outputDir: String?
) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)
    private val outputFile = outputDir?.let {
        rootDir.resolve(it).resolve("registered.csv")
    }

    fun run(): Int {
        var errorCount = 0
        writeOutputFileHeader()
        subjects.forEach { (subject, path, type, references: List<SchemaReference>, localReferences, metaData, ruleSet, normalize) ->
            try {
                val schemaId = registerSchema(subject, path, type.toSchemaType(), references, localReferences, metaData, ruleSet, normalize)
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
        references: List<SchemaReference>,
        localReferences: List<LocalReference>,
        metadata: Metadata,
        ruleSet: RuleSet,
        normalize: Boolean
    ): Int {
        val parsedSchema = SchemaParser
            .provide(type, client, rootDir)
            .parseSchemaFromFile(subject, path, references, localReferences, metadata, ruleSet)
        logger.infoIfNotQuiet("Registering $subject (from $path)")
        val schemaId = client.register(subject, parsedSchema, normalize)
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
