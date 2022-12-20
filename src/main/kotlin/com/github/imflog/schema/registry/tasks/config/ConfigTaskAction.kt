package com.github.imflog.schema.registry.tasks.config

import io.confluent.kafka.schemaregistry.CompatibilityLevel
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import org.gradle.api.logging.Logging

class ConfigTaskAction(
    private val client: SchemaRegistryClient,
    private val subjects: List<ConfigSubject>
) {

    private val logger = Logging.getLogger(ConfigTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        for ((subject, config) in subjects) {
            logger.debug("$subject: setting config $config")
            try {
                if (CompatibilityLevel.forName(config) == null) {
                    logger.error("'$config' is not a valid schema registry compatibility")
                    errorCount++
                } else {
                    client.updateCompatibility(subject, config)
                }
            } catch (ex: RestClientException) {
                logger.error("Error during compatibility update for $subject", ex)
                errorCount++
            }
        }
        return errorCount
    }
}
