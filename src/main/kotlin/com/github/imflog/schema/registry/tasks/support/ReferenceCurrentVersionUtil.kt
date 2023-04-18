package com.github.imflog.schema.registry.tasks.support

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.gradle.api.GradleScriptException
import org.gradle.api.logging.Logging

object ReferenceCurrentVersionUtil {
    private val logger = Logging.getLogger(ReferenceCurrentVersionUtil::class.java)

    fun updateNonPositiveReferencesToCurrentVersion(
        client: SchemaRegistryClient,
        references: List<SchemaReference>
    ) {
        references.forEach {
            if (it.version <= 0) {
                it.version = getReferenceCurrentVersion(client, it)
            }
        }
    }

    private fun getReferenceCurrentVersion(
        client: SchemaRegistryClient,
        reference: SchemaReference
    ): Int {
        logger.debug("Fetching latest remote version for '$reference'")

        val schemas = client.getSchemas(reference.subject, false, true);
        if (schemas.size == 0) {
            throw GradleScriptException("Did not find any schemas with the subject '${reference.subject}", Throwable())
        }
        if (schemas.size > 1) {
            throw GradleScriptException("Found more than one schema with the subject prefix '${reference.subject}.", Throwable())
        }

        return client.getVersion(reference.subject, schemas[0])
    }
}