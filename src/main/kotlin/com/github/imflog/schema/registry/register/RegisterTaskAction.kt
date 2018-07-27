package com.github.imflog.schema.registry.register

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class RegisterTaskAction(
        val client: SchemaRegistryClient,
        val subjects: ArrayList<Pair<String, String>>,
        val rootDir: File
) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { (subject, path) ->
            try {
                registerSchema(subject, path)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun registerSchema(subject: String, path: String) {
        val schema = readSchema(path)
        logger.debug("Calling register ($subject, $path)")
        client.register(subject, schema)
    }

    private fun readSchema(path: String): Schema {
        val parser = Schema.Parser()
        val schemaContent = File(rootDir, path).readText()
        return parser.parse(schemaContent)
    }
}