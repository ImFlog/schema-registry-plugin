package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TYPE = "type"

private const val ITEMS = "items"

private const val VALUES = "values"

private const val FIELDS = "fields"

class AvroSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.AVRO

    private val keysToUpdate: Collection<String> = listOf(TYPE, ITEMS, VALUES, FIELDS)

    override fun resolveLocalReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>
    ): String {
        return replaceType(JSONObject(schemaContent),localReferences).toString()
    }

    private fun replaceType(type: Any?, localReferences: List<LocalReference>): Any? {
        return if (type != null) when (type) {
            is String -> localReferences
                .filter { it.name == type }
                .map { JSONObject(it.content(rootDir)) }
                .map { replaceType(it, localReferences) }
                .firstOrNull() ?: type
            is JSONArray -> JSONArray(type.map { replaceType(it, localReferences) })
            is JSONObject -> {
                    type.keySet()
                        .filter { keysToUpdate.contains(it) }
                        .forEach { type.put(it, replaceType(type.get(it), localReferences)) }
                    return type
                }
            else -> type
        } else null
    }
}