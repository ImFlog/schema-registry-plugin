package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import java.util.HashMap



/**
 * This is a singleton.
 * We can reuse the registryClient without instantiating new ones.
 */
object RegistryClientWrapper {

    private var registryClient: SchemaRegistryClient? = null

    private const val BASIC_AUTH_SOURCE: String = "USER_INFO"

    fun client(url: String, auth: SchemaRegistryBasicAuth): SchemaRegistryClient? {
        if (registryClient == null) {

            registryClient = CachedSchemaRegistryClient(url, 100, getConfig(auth))
        }
        return registryClient
    }

    fun getConfig(auth: SchemaRegistryBasicAuth): HashMap<String, String> {
        val config = HashMap<String, String>()
        if (!auth.username.isNullOrEmpty() && !auth.password.isNullOrEmpty()) {
            // Note that BASIC_AUTH_CREDENTIALS_SOURCE is not configurable as the plugin only supports
            // a single schema registry URL, so there is no additional utility of the URL source.
            config[SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = BASIC_AUTH_SOURCE
            config[SchemaRegistryClientConfig.USER_INFO_CONFIG] = auth.getBasicAuthCredentials()
        }
        return config
    }
}