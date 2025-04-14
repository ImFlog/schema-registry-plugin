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

    fun client(url: String, config: Map<String, String>): SchemaRegistryClient =
        CachedSchemaRegistryClient(
            listOf(url),
            100,
            listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()),
            config,
        )
}
