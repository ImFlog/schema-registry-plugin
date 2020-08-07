package com.github.imflog.schema.registry.download

import com.github.imflog.schema.registry.schemaTypeToExtension
import com.github.imflog.schema.registry.toNullable
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import java.io.File

class DownloadTaskAction(
    private val client: SchemaRegistryClient,
    private val subjects: List<DownloadSubject>,
    private val rootDir: File
) {

    private val logger = Logging.getLogger(DownloadTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { downloadSubject ->
            logger.info("Start loading schemas for ${downloadSubject.subject}")
            try {
                // TODO : Better error handling ?
                val downloadedSchema =
                    downloadSchema(downloadSubject) ?: throw GradleException("Could not find the requested schema")
                writeSchemaFiles(downloadSubject, downloadedSchema)
            } catch (e: Exception) {
                logger.error("Error during schema retrieval for ${downloadSubject.subject}", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun downloadSchema(subject: DownloadSubject): ParsedSchema? {
        val schemaMetadata = if (subject.version == null) {
            client.getLatestSchemaMetadata(subject.subject)
        } else {
            client.getSchemaMetadata(subject.subject, subject.version)
        }
        return client.parseSchema(
            schemaMetadata.schemaType,
            schemaMetadata.schema,
            schemaMetadata.references
        ).toNullable()
    }

    private fun writeSchemaFiles(downloadSubject: DownloadSubject, schema: ParsedSchema) {
        val outputDir = File(rootDir, downloadSubject.file)
        outputDir.mkdirs()
        // TODO: Handle error
        val extension = schemaTypeToExtension(schema.schemaType()) ?: throw Exception("Unknown type provided")
        val outputFile = File(outputDir, "${downloadSubject.subject}.$extension")
        outputFile.createNewFile()
        logger.info("Writing file  $outputFile")
        outputFile.printWriter().use { out ->
            out.println(schema.toString())
        }
    }
}
