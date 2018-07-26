package com.github.imflog.schema.registry.register

import com.github.imflog.schema.registry.StringFileSubject
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class RegisterTaskAction(
        val client: SchemaRegistryClient,
        val subjects: ArrayList<StringFileSubject>,
        val rootDir: File
) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { subject ->
            try {
                registerSchema(subject)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun registerSchema(stringFileSubject: StringFileSubject) {
        val schema = readSchema(stringFileSubject.path)
        logger.debug("Calling register (${stringFileSubject.subject}, ${stringFileSubject.path})")
        client.register(stringFileSubject.subject, schema)
    }

    private fun readSchema(path: String): Schema {
        val parser = Schema.Parser()
        val schemaContent = File(rootDir, path).readText()
        return parser.parse(schemaContent)
    }
}