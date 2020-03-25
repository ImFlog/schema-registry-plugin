package com.github.imflog.schema.registry.download

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
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
                val downloadedSchema = downloadSchema(downloadSubject)
                writeSchemaFiles(downloadSubject, downloadedSchema)
            } catch (e: Exception) {
                logger.error("Error during schema retrieval for ${downloadSubject.subject}", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun downloadSchema(subject: DownloadSubject): Schema {
        val schemaMetadata = if (subject.version == null) {
            client.getLatestSchemaMetadata(subject.subject)
        } else {
            client.getSchemaMetadata(subject.subject, subject.version)
        }
        return Schema.Parser().parse(schemaMetadata.schema)
    }

    private fun writeSchemaFiles(downloadSubject: DownloadSubject, schemas: Schema) {
        val outputDir = File(rootDir, downloadSubject.file)
        outputDir.mkdirs()
        val outputFile = File(outputDir, "${downloadSubject.subject}.avsc")
        outputFile.createNewFile()
        logger.info("Writing file  $outputFile")
        outputFile.printWriter().use { out ->
            out.println(schemas.toString(true))
        }
    }
}
