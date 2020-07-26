package com.github.imflog.schema.registry.compatibility

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.rest.exceptions.Errors
import org.apache.avro.Schema
import org.gradle.api.logging.Logging
import java.io.File
import java.io.IOException

class CompatibilityTaskAction(
    private val client: SchemaRegistryClient,
    private val subjects: List<CompatibilitySubject>,
    private val rootDir: File
) {

    private val logger = Logging.getLogger(CompatibilityTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        for ((subject, path, dependencies) in subjects) {
            logger.debug("Loading schema for subject($subject) from $path.")
            val parsedSchema: Schema = parseSchemas(path, dependencies)
            val isCompatible = try {
                client.testCompatibility(subject, parsedSchema)
            } catch (ioEx: IOException) {
                logger.error("", ioEx)
                false
            } catch (restEx: RestClientException) {
                // If the subject does not exist, it is compatible
                if (restEx.errorCode == Errors.SUBJECT_NOT_FOUND_ERROR_CODE) {
                    true
                } else {
                    logger.error("", restEx)
                    false
                }
            }
            if (isCompatible) {
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
