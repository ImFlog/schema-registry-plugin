package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.LoggingUtils.infoIfNotQuiet
import com.github.imflog.schema.registry.Subject
import com.github.imflog.schema.registry.parser.SchemaParser
import com.github.imflog.schema.registry.toSchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.rest.exceptions.Errors
import org.gradle.api.logging.Logging
import java.io.File
import java.io.IOException

class CompatibilityTaskAction(
    private val client: SchemaRegistryClient,
    private val rootDir: File,
    private val subjects: List<Subject>,
) {

    private val logger = Logging.getLogger(CompatibilityTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { subject ->
            logger.debug("Loading schema for subject(${subject.inputSubject}) from ${subject.file}.")
            val isCompatible = try {
                val parsedSchema = SchemaParser
                    .provide(subject.type.toSchemaType(), client, rootDir)
                    .parseSchemaFromFile(subject)
                val isCompatible = client.testCompatibility(subject.inputSubject, parsedSchema)
                if (!isCompatible) {
                    try {
                        client.testCompatibilityVerbose(subject.inputSubject, parsedSchema).forEach {
                            logger.error("Returned errors : $it")
                        }
                    } catch (_: Exception) {
                        // If we use a confluent version < 6.1.0 this call may fail as the API response would be a boolean instead of the expected String list.
                    }
                }
                isCompatible
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
                logger.infoIfNotQuiet("Schema ${subject.file} is compatible with subject: ${subject.inputSubject}")
            } else {
                logger.error("Schema ${subject.file} is not compatible with subject: ${subject.inputSubject}")
                errorCount++
            }
        }
        return errorCount
    }
}
