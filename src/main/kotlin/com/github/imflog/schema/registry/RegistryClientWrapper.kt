package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient

/**
 * This is a singleton.
 * We can reuse the registryClient without instantiating new ones.
 */
object RegistryClientWrapper {

    private var registryClient: SchemaRegistryClient? = null

    fun client(url: String): SchemaRegistryClient? {
        if (registryClient == null) {
            registryClient = CachedSchemaRegistryClient(url, 100)
        }
        return registryClient
    }
}