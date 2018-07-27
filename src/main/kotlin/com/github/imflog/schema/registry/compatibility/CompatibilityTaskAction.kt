package com.github.imflog.schema.registry.compatibility

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class CompatibilityTaskAction(
        val client: SchemaRegistryClient,
        val subjects: ArrayList<Pair<String, String>>,
        val rootDir: File) {

    private val logger = Logging.getLogger(CompatibilityTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        for ((subject, path) in subjects) {
            logger.debug("Loading schema for subject($subject) from $path.")
            val parsedSchema: Schema = lookupSchemas(path)
            val compatible = client.testCompatibility(subject, parsedSchema)
            if (compatible) {
                logger.info("Schema $path is compatible with subject($subject)")
            } else {
                logger.error("Schema $path is not compatible with subject($subject)")
                errorCount++
            }
        }
        return errorCount
    }

    private fun lookupSchemas(path: String): Schema {

        val schemaContent = File(rootDir, path).readText()
        val parser = Schema.Parser()
        return parser.parse(schemaContent)
    }
}
