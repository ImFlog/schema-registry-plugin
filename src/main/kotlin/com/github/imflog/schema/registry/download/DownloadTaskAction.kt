package com.github.imflog.schema.registry.download

import com.github.imflog.schema.registry.BaseTaskAction
import com.github.imflog.schema.registry.UnknownSchemaTypeException
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import org.gradle.api.logging.Logging
import java.io.File

class DownloadTaskAction(
    client: SchemaRegistryClient,
    rootDir: File,
    private val subjects: List<DownloadSubject>
) : BaseTaskAction(client, rootDir) {

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

    private fun downloadSchema(subject: DownloadSubject): ParsedSchema {
        val schemaMetadata = if (subject.version == null) {
            client.getLatestSchemaMetadata(subject.subject)
        } else {
            client.getSchemaMetadata(subject.subject, subject.version)
        }
        return parseSchema(subject.subject, schemaMetadata.schema, schemaMetadata.schemaType, schemaMetadata.references)
    }

    private fun writeSchemaFiles(downloadSubject: DownloadSubject, schema: ParsedSchema) {
        val outputDir = File(rootDir, downloadSubject.file)
        outputDir.mkdirs()
        val outputFile = File(outputDir, "${downloadSubject.subject}.${schema.extension()}")
        outputFile.createNewFile()
        logger.info("Writing file  $outputFile")
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
