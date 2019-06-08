package com.github.imflog.schema.registry.config

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import org.gradle.api.logging.Logging

class ConfigTaskAction(
    private val client: SchemaRegistryClient,
    private val subjectPairs: List<Pair<String, String>>
) {

    private val logger = Logging.getLogger(ConfigTaskAction::class.java)

    fun run() : Int {
        var errorCount = 0
        for ((subject, config) in subjectPairs) {
            logger.debug("$subject: setting config $config")
            try {
                client.updateCompatibility(subject, config)
            } catch (ex: RestClientException) {
                logger.error("Error during compatibility update for $subject", ex)
                errorCount++
            }
        }
        return errorCount
    }
}