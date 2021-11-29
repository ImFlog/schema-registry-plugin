package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema

enum class SchemaType(val registryType: String, val extension: String) {
    AVRO(AvroSchema.TYPE, "avsc"),
    PROTOBUF(ProtobufSchema.TYPE, "proto"),
    JSON(JsonSchema.TYPE, "json")
}

fun String.toSchemaType(): SchemaType = when (this) {
    AvroSchema.TYPE -> SchemaType.AVRO
    ProtobufSchema.TYPE -> SchemaType.PROTOBUF
    JsonSchema.TYPE -> SchemaType.JSON
    else -> throw UnknownSchemaTypeException(this)
}
