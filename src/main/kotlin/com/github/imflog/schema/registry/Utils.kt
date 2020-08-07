package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import java.io.File
import java.util.Optional

/**
 * Utility function that convert Java Optional to Kotlin null
 */
fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null);

fun schemaTypeToExtension(type: String): String? = when (type) {
    AvroSchema.TYPE -> "avsc"
    ProtobufSchema.TYPE -> "proto"
    JsonSchema.TYPE -> "json"
    else -> null
}
