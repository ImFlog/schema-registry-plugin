package com.github.imflog.schema.registry.config

import io.confluent.kafka.schemaregistry.avro.AvroCompatibilityLevel
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import org.gradle.api.logging.Logging

class ConfigTaskAction(
    private val client: SchemaRegistryClient,
    private val subjects: List<ConfigSubject>
) {

    private val logger = Logging.getLogger(ConfigTaskAction::class.java)

    fun run() : Int {
        var errorCount = 0
        for ((subject, config) in subjects) {
            logger.debug("$subject: setting config $config")
            try {
                // validate that subject pair includes a valid AvroCompatibilityValue:
                // can't use the enum directly due to https://youtrack.jetbrains.net/issue/KT-31244
                AvroCompatibilityLevel.valueOf(config)
                client.updateCompatibility(subject, config)
            } catch (ex: IllegalArgumentException) {
                logger.error("'$config' is not a valid schema registry compatibility", ex)
                errorCount++
            } catch (ex: RestClientException) {
                logger.error("Error during compatibility update for $subject", ex)
                errorCount++
            }
        }
        return errorCount
    }
}
