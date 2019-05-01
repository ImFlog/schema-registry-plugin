package com.github.imflog.schema.registry.compatibility

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class CompatibilityTaskAction(
    val client: SchemaRegistryClient,
    val subjects: List<Triple<String, String, List<String>>>,
    val rootDir: File
) {

    private val logger = Logging.getLogger(CompatibilityTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        for ((subject, path, dependencies) in subjects) {
            logger.debug("Loading schema for subject($subject) from $path.")
            val parsedSchema: Schema = parseSchemas(path, dependencies)
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

    private fun parseSchemas(path: String, dependencies: List<String>): Schema {
        val parser = Schema.Parser()
        loadDependencies(dependencies, parser)
        return parseSchema(parser, path)
    }

    /**
     * This adds all record names to the current parser known types.
     */
    private fun loadDependencies(dependencies: List<String>, parser: Schema.Parser) {
        dependencies.reversed().forEach {
            parseSchema(parser, it)
        }
    }

    private fun parseSchema(parser: Schema.Parser, path: String): Schema {
        val schemaContent = File(rootDir, path).readText()
        return parser.parse(schemaContent)
    }
}
