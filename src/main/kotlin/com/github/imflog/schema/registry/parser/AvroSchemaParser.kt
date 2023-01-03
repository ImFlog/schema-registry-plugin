package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TYPE = "type"

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
        val jsonObj = JSONObject(schemaContent)
        if(jsonObj.has(FIELDS)) {
            jsonObj.put(FIELDS,
                JSONArray(jsonObj
                    .getJSONArray(FIELDS)
                    .toMutableList()
                    .mapInPlace { if (it is JSONObject) replaceLocalReference(it, localReferences) else it }
                )
            )
        }
        return jsonObj.toString()
    }

    fun replaceLocalReference(jsonObject: JSONObject, localReferences: List<LocalReference>): JSONObject {
        if(jsonObject.has(TYPE)) {
            jsonObject.put(TYPE, replaceType(jsonObject.opt(TYPE),localReferences))
        }
        return jsonObject
    }

    fun replaceType(type: Any, localReferences: List<LocalReference>): Any{
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

    inline fun <T> MutableList<T>.mapInPlace(mutator: (T)->T):MutableList<T> {
        this.forEachIndexed { idx, value ->
            mutator(value).let { newValue ->
                if (newValue !== value) this.set(idx,newValue)
            }
        }
        return this;
    }
}