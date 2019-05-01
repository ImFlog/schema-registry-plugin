package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig


/**
 * This is a singleton.
 * We can reuse the registryClient without instantiating new ones.
 */
object RegistryClientWrapper {

    private const val BASIC_AUTH_SOURCE: String = "USER_INFO"

    fun client(url: String, auth: SchemaRegistryBasicAuthExtension): SchemaRegistryClient =
        CachedSchemaRegistryClient(url, 100, getConfig(auth))

    /**
     * Retrieves configuration from the plugin extension.
     * Note that BASIC_AUTH_CREDENTIALS_SOURCE is not configurable as the plugin only supports
     * a single schema registry URL, so there is no additional utility of the URL source.
     */
    private fun getConfig(auth: SchemaRegistryBasicAuthExtension): Map<String, String> {
        return if (auth.username == null || auth.password == null) {
            mapOf()
        } else {
            mapOf(
                SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to BASIC_AUTH_SOURCE,
                SchemaRegistryClientConfig.USER_INFO_CONFIG to auth.getBasicAuthCredentials()
            )
        }
    }
}
