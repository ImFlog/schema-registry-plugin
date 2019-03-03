package com.github.imflog.schema.registry.register

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class RegisterTaskAction(
        val client: SchemaRegistryClient,
        val subjects: List<Triple<String, String, List<String>>>,
        val rootDir: File
) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { (subject, path, dependencies) ->
            try {
                registerSchema(subject, path, dependencies)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun registerSchema(subject: String, path: String, dependencies: List<String>) {
        val parser = Schema.Parser()
        loadDependencies(dependencies, parser)
        val schema = readSchema(parser, path)
        logger.debug("Calling register ($subject, $path)")
        client.register(subject, schema)
    }

    /**
     * This adds all record names to the current parser known types.
     */
    private fun loadDependencies(dependencies: List<String>, parser: Schema.Parser) {
        dependencies.reversed().forEach {
            readSchema(parser, it)
        }
    }

    private fun readSchema(parser: Schema.Parser, path: String): Schema {
        val schemaContent = File(rootDir, path).readText()
        return parser.parse(schemaContent)
    }
}