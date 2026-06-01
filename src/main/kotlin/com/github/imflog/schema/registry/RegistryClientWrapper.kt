package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider


/**
 * This is a singleton.
 * We can reuse the registryClient without instantiating new ones.
 */
object RegistryClientWrapper {

    private const val HEADER_PREFIX = "request.header."

    fun client(url: String, config: Map<String, String>): SchemaRegistryClient =
        CachedSchemaRegistryClient(
            listOf(url),
            100,
            listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()),
            config,
            buildHeaders(config)
        )

    private fun buildHeaders(config: Map<String, String>): Map<String, String>? {
        val headers = config
            .filterKeys { it.startsWith(HEADER_PREFIX) }
            .mapKeys { it.key.removePrefix(HEADER_PREFIX) }
        return headers.ifEmpty { null }
    }
}
