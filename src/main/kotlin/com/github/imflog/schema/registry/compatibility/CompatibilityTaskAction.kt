package com.github.imflog.schema.registry.compatibility

import com.github.imflog.schema.registry.StringFileSubject
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File

class CompatibilityTaskAction(
        val client: SchemaRegistryClient,
        val subjects: ArrayList<StringFileSubject>,
        val rootDir: File) {

    private val logger = Logging.getLogger(CompatibilityTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        for (subject in subjects) {
            val subjectPair = lookupSchemas(subject)
            val compatible = client.testCompatibility(subjectPair.first, subjectPair.second)
            if (compatible) {
                logger.info("Schema ${subject.path} is compatible with subject(${subjectPair.first})")
            } else {
                logger.error("Schema ${subject.path} is not compatible with subject(${subject.subject})")
                errorCount++
            }
        }
        return errorCount
    }

    private fun lookupSchemas(subject: StringFileSubject): Pair<String, Schema> {
        logger.debug("Loading schema for subject(${subject.subject}) from ${subject.path}.")
        val schemaContent = File(rootDir, subject.path).readText()
        val parser = Schema.Parser()
        val parsedSchema = parser.parse(schemaContent)
        return Pair(subject.subject, parsedSchema)
    }
}
