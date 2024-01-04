package com.github.imflog.schema.registry.tasks.download

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.github.imflog.schema.registry.LoggingUtils.infoIfNotQuiet
import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.toSchemaType
import com.google.common.base.Suppliers
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.gradle.api.logging.Logging
import java.io.File
import java.util.regex.PatternSyntaxException

class DownloadTaskAction(
    private val client: SchemaRegistryClient,
    private val rootDir: File,
    private val subjects: List<DownloadSubject>,
    private val metadataConfiguration: MetadataExtension,
    private val pretty: Boolean = false
) {

    private val logger = Logging.getLogger(DownloadTaskAction::class.java)
    private val objectMapper = ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    fun run(): Int {
        var errorCount = 0
        expandSubjectPatterns().forEach { downloadSubject ->
            logger.infoIfNotQuiet("Start loading schemas for ${downloadSubject.subject}")
            try {
                val metadata = getSchemaMetadata(downloadSubject)
                val outputDir = File(rootDir.toURI()).resolve(downloadSubject.outputPath)
                outputDir.mkdirs()
                if (metadataConfiguration.enabled) {
                    val metadataDirectory = metadataConfiguration.outputPath?.run {
                        File(rootDir.toURI()).resolve(this)
                    } ?: outputDir
                    metadataDirectory.mkdirs()
                    writeSchemaMetadata(downloadSubject, metadata, metadataDirectory)
                }
                writeSchemaFile(downloadSubject, metadata, pretty, outputDir)
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

    private fun getSchemaMetadata(subject: DownloadSubject): SchemaMetadata =
        if (subject.version == null) client.getLatestSchemaMetadata(subject.subject)
        else client.getSchemaMetadata(subject.subject, subject.version)

    private fun writeSchemaFile(downloadSubject: DownloadSubject, schemaMetadata: SchemaMetadata, pretty: Boolean, outputDir: File) {
        val parsedSchema = parseSchemaWithRemoteReferences(
            downloadSubject.subject,
            schemaMetadata.schemaType.toSchemaType(),
            schemaMetadata.schema,
            schemaMetadata.references
        )
        val fileName = downloadSubject.outputFileName ?: downloadSubject.subject
        val outputFile = File(outputDir, "${fileName}.${parsedSchema.schemaType().toSchemaType().extension}")
        outputFile.createNewFile()
        logger.infoIfNotQuiet("Writing file $outputFile")
        outputFile.printWriter().use { out ->
            out.println(getSchemaString(parsedSchema, pretty))
        }
    }

    private fun getSchemaString(parsedSchema: ParsedSchema, pretty: Boolean): String {
        return if (pretty && isSupportedPrettyType(parsedSchema)) objectMapper.readTree(parsedSchema.toString()).toPrettyString() else parsedSchema.toString()
    }

    /**
     * Checks whether the current schema type should be pretty-printed.
     * Avro and Json are considered eligible for pretty formatting, Protobuf is not.
     */
    private fun isSupportedPrettyType(parsedSchema: ParsedSchema): Boolean {
        return parsedSchema.schemaType() == AvroSchema.TYPE || parsedSchema.schemaType() == JsonSchema.TYPE
    }

    private fun writeSchemaMetadata(downloadSubject: DownloadSubject, schemaMetadata: SchemaMetadata, outputDir: File) {
        val fileName = downloadSubject.outputFileName ?: downloadSubject.subject
        val outputFile = File(outputDir, "${fileName}-metadata.json")
        outputFile.createNewFile()
        logger.infoIfNotQuiet("Writing metadata file $outputFile")
        outputFile.printWriter().use { out ->
            out.println(
                objectMapper.writeValueAsString(schemaMetadata)
            )
        }
    }

    private fun parseSchemaWithRemoteReferences(
        subject: String,
        schemaType: SchemaType,
        schemaContent: String,
        references: List<SchemaReference>,
    ): ParsedSchema = client
        .parseSchema(schemaType.registryType, schemaContent, references)
        .orElseThrow { SchemaParsingException(subject, schemaType) }
}
