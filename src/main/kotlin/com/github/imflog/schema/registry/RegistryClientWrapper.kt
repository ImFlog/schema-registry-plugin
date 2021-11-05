package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig.CLIENT_NAMESPACE
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory


/**
 * This is a singleton.
 * We can reuse the registryClient without instantiating new ones.
 */
object RegistryClientWrapper {

    private val logger = LoggerFactory.getLogger(RegistryClientWrapper::class.java)

    private const val BASIC_AUTH_SOURCE: String = "USER_INFO"

    fun client(url: String, basicAuth: String, sslConfigs: Map<String, String>): SchemaRegistryClient =
        CachedSchemaRegistryClient(
            listOf(url),
            100,
            listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()),
            getConfig(basicAuth) + getValidatedSslConfig(sslConfigs)
        )

    /**
     * Retrieves configuration from the plugin extension.
     * Note that BASIC_AUTH_CREDENTIALS_SOURCE is not configurable as the plugin only supports
     * a single schema registry URL, so there is no additional utility of the URL source.
     */
    private fun getConfig(basicAuth: String): Map<String, String> = if (basicAuth == ":")
        mapOf()
    else
        mapOf(
            SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to BASIC_AUTH_SOURCE,
            SchemaRegistryClientConfig.USER_INFO_CONFIG to basicAuth
        )

    /**
     * Validates that an SSLConfig map only contains only keys that starts with "ssl.xxx".
     * @see org.apache.kafka.common.config.SslConfigs
     */
    private fun getValidatedSslConfig(sslConfigs: Map<String, Any>): Map<String, Any> {
        sslConfigs
            .keys
            .filterNot { property -> property.startsWith("ssl.") }
            .let { wrongProperties ->
                if (wrongProperties.any()) {
                    wrongProperties.forEach { property -> logger.error("$property is not a valid sslConfig") }
                    throw GradleException(
                        "SSL configuration only accept keys from org.apache.kafka.common.config.SslConfigs"
                    )
                }
            }
        return sslConfigs.mapKeys { CLIENT_NAMESPACE + it.key }
    }
}
