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

    fun client(url: String, userInfo: String): SchemaRegistryClient? {
        if (registryClient == null) {
            val config = HashMap<String, String>()
            if (!userInfo.isEmpty()) {
                // Note that BASIC_AUTH_CREDENTIALS_SOURCE is not configurable as the plugin only supports
                // a single schema registry URL, so there is no additional utility of the URL source.
                config[SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
                config[SchemaRegistryClientConfig.USER_INFO_CONFIG] = userInfo
            }
            registryClient = CachedSchemaRegistryClient(url, 100, config)
        }
        return registryClient
    }
}