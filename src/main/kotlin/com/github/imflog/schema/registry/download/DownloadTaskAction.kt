package com.github.imflog.schema.registry.download

import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class DownloadTaskAction(
        private val client: SchemaRegistryClient,
        private val subjects: MutableList<String>,
        private val outputDir: File
) {

    private val logger = Logging.getLogger(DownloadTaskAction::class.java)

    fun run(): Int {
        logger.info("Start loading schemas for $subjects")
        var errorCount = 0
        subjects.forEach { subject ->
            try {
                val downloadedSchema = downloadSchema(subject)
                writeSchemas(subject, downloadedSchema)
            } catch (e: Exception) {
                logger.error("Error during schema retrieval for $subject")
                errorCount++
            }
        }
        return errorCount
    }

    private fun downloadSchema(subject: String): Schema {
        val latestSchemaMetadata: SchemaMetadata? = client.getLatestSchemaMetadata(subject)
        val parser = Schema.Parser()
        return parser.parse(latestSchemaMetadata?.schema)
    }

    private fun writeSchemas(subject: String, schemas: Schema) {
        val outputFile = File(outputDir, "$subject.avsc")
        logger.info("Writing file  $outputFile")
        outputFile.printWriter().use { out ->
            out.println(schemas.toString(true))
        }
    }

}