package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.tasks.BaseTaskAction
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.rest.exceptions.Errors
import java.io.File
import java.io.IOException
import org.gradle.api.logging.Logging

class CompatibilityTaskAction(
    client: SchemaRegistryClient,
    rootDir: File,
    private val subjects: List<CompatibilitySubject>,
    quietLogging: Boolean
) : BaseTaskAction(client, rootDir, quietLogging) {

    private val logger = Logging.getLogger(CompatibilityTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        for ((subject, path, type, dependencies) in subjects) {
            logger.debug("Loading schema for subject($subject) from $path.")
            val isCompatible = try {
                val parsedSchema = parseSchemaFromFile(path, type, dependencies)
                val isCompatible = client.testCompatibility(subject, parsedSchema)
                if (!isCompatible) {
                    try {
                        client.testCompatibilityVerbose(subject, parsedSchema).forEach {
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
                logger.infoIfNotQuiet("Schema $path is compatible with subject: $subject")
            } else {
                logger.error("Schema $path is not compatible with subject: $subject")
                errorCount++
            }
        }
        return errorCount
    }
}
