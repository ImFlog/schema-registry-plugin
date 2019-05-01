package com.github.imflog.schema.registry.download

import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class DownloadTaskAction(
    private val client: SchemaRegistryClient,
    private val subjectPairs: List<Pair<String, String>>,
    private val rootDir: File
) {

    private val logger = Logging.getLogger(DownloadTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjectPairs.forEach { pair ->
            val (subject, path) = pair
            logger.info("Start loading schemas for $subject")
            try {
                val downloadedSchema = downloadSchema(subject)
                writeSchemas(subject, downloadedSchema, path)
            } catch (e: Exception) {
                logger.error("Error during schema retrieval for $subject", e)
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

    private fun writeSchemas(subject: String, schemas: Schema, path: String) {
        val outputDir = File(rootDir, path)
        outputDir.mkdirs()
        val outputFile = File(outputDir, "$subject.avsc")
        outputFile.createNewFile()
        logger.info("Writing file  $outputFile")
        outputFile.printWriter().use { out ->
            out.println(schemas.toString(true))
        }
    }
}
