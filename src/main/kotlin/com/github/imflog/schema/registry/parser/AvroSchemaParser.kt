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

    override fun resolveLocalReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>
    ): String {
        return replaceLocalReference(JSONObject(schemaContent),localReferences).toString()
    }

    private fun replaceLocalReference(jsonObject: JSONObject, localReferences: List<LocalReference>): JSONObject {
        when {
            jsonObject.has(FIELDS) -> jsonObject.put(FIELDS,
                JSONArray(jsonObject
                    .getJSONArray(FIELDS)
                    .map { if (it is JSONObject) replaceLocalReference(it, localReferences) else it }
                )
            )
            jsonObject.has(TYPE) -> jsonObject.put(TYPE, replaceType(jsonObject.get(TYPE), localReferences))
            jsonObject.has(ITEMS) -> jsonObject.put(ITEMS, replaceType(jsonObject.get(ITEMS), localReferences))
            jsonObject.has(VALUES) -> jsonObject.put(VALUES, replaceType(jsonObject.get(VALUES), localReferences))
        }
        return jsonObject
    }

    private fun replaceType(type: Any, localReferences: List<LocalReference>): Any {
        return when (type) {
            is String -> localReferences
                .filter { it.name == type }
                .map { JSONObject(it.content(rootDir)) }
                .map { replaceLocalReference(it, localReferences) }
                .firstOrNull() ?: type

            is JSONArray -> JSONArray(type.map { replaceType(it, localReferences) })
            is JSONObject -> replaceLocalReference(type, localReferences)
            else -> type
        }
    }
}