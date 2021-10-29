package com.github.imflog.schema.registry.tasks.download

import com.github.imflog.schema.registry.UnknownSchemaTypeException
import com.github.imflog.schema.registry.tasks.BaseTaskAction
import com.google.common.base.Suppliers
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import java.io.File
import java.util.regex.PatternSyntaxException
import org.gradle.api.logging.Logging

class DownloadTaskAction(
    client: SchemaRegistryClient,
    rootDir: File,
    private val subjects: List<DownloadSubject>,
    quietLogging: Boolean
) : BaseTaskAction(client, rootDir, quietLogging) {

    private val logger = Logging.getLogger(DownloadTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        expandSubjectPatterns().forEach { downloadSubject ->
            logger.infoIfNotQuiet("Start loading schemas for ${downloadSubject.subject}")
            try {
                val downloadedSchema = downloadSchema(downloadSubject)
                writeSchemaFiles(downloadSubject, downloadedSchema)
            } catch (e: Exception) {
                logger.error("Error during schema retrieval for ${downloadSubject.subject}", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun expandSubjectPatterns(): List<DownloadSubject> {
        val subjectsSupplier = Suppliers.memoize { client.allSubjects }
        return subjects.flatMap { downloadSubject ->
            if (downloadSubject.regex) {
                parseSubjectRegex(downloadSubject.subject)?.let { regex ->
                    subjectsSupplier.get()
                        .filter { subject -> regex.matches(subject) }
                        .map { subject ->
                            DownloadSubject(
                                subject,
                                downloadSubject.outputPath,
                                downloadSubject.version
                            )
                        }
                        .toList()
                } ?: emptyList()
            } else {
                listOf(downloadSubject)
            }
        }
    }

    private fun parseSubjectRegex(regex: String): Regex? {
        return try {
            Regex(regex)
        } catch (exception: PatternSyntaxException) {
            logger.error("Unable to compile subject pattern of $regex, skipping", exception)
            null
        }
    }

    private fun downloadSchema(subject: DownloadSubject): ParsedSchema {
        val schemaMetadata = if (subject.version == null) {
            client.getLatestSchemaMetadata(subject.subject)
        } else {
            client.getSchemaMetadata(subject.subject, subject.version)
        }
        return parseSchema(
            subject.subject,
            schemaMetadata.schema,
            schemaMetadata.schemaType,
            schemaMetadata.references,
            mapOf(), // No need for local reference when downloading a schema
        )
    }

    private fun writeSchemaFiles(downloadSubject: DownloadSubject, schema: ParsedSchema) {
        val outputDir = File(rootDir.toURI()).resolve(downloadSubject.outputPath)
        outputDir.mkdirs()
        val fileName = downloadSubject.outputFileName ?: downloadSubject.subject
        val outputFile = File(outputDir, "${fileName}.${schema.extension()}")
        outputFile.createNewFile()
        logger.infoIfNotQuiet("Writing file  $outputFile")
        outputFile.printWriter().use { out ->
            out.println(schema.toString())
        }
    }
}

fun ParsedSchema.extension(): String = when (val type = this.schemaType()) {
    AvroSchema.TYPE -> "avsc"
    ProtobufSchema.TYPE -> "proto"
    JsonSchema.TYPE -> "json"
    else -> throw UnknownSchemaTypeException(type)
}
