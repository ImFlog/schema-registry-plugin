package com.github.imflog.schema.registry.register

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class RegisterTaskAction(
        val client: SchemaRegistryClient,
        val subjects: ArrayList<Pair<String, List<String>>>,
        val rootDir: File
) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { (subject, paths) ->
            try {
                registerSchema(subject, paths)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun registerSchema(subject: String, paths: List<String>) {
        val schema = readSchema(paths)
        logger.debug("Calling register ($subject)")
        client.register(subject, schema)
    }

    private fun readSchema(paths: List<String>): Schema {
        val parser = Schema.Parser()
        val schemas = ArrayList<Schema>()
        paths.forEach { path ->
            val schemaContent = File(rootDir, path).readText()
            val schema = parser.parse(schemaContent);
            schemas.add(schema)
        }
        return schemas.last()
    }
}