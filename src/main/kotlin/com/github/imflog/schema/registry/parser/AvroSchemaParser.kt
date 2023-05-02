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

private const val NAMESPACE = "namespace"

private const val NAME = "name"

class AvroSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.AVRO

    private val keysToUpdate: Collection<String> = listOf(TYPE, ITEMS, VALUES, FIELDS)

    private val referenceUsageCount: MutableMap<String, Int> = mutableMapOf()

    override fun resolveLocalReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>
    ): String {
        val rootObj = JSONObject(schemaContent)
        val rootNamespace = if (rootObj.has(NAMESPACE))
            rootObj.getString(NAMESPACE)
        else ""
        return replaceType(rootObj, localReferences, rootNamespace).toString()
    }

    private fun replaceType(type: Any, localReferences: List<LocalReference>, rootNamespace: String): Any {
        return when (type) {
            is String -> {
                val matchedReference = localReferences.firstOrNull { it.name == type } ?: return type
                val currentReferenceCount = referenceUsageCount.getOrDefault(matchedReference.name, 0).inc()
                referenceUsageCount[matchedReference.name] = currentReferenceCount
                val referenceContent = JSONObject(matchedReference.content(rootDir))
                if (matchedReference.getNamespace() == rootNamespace) {
                    referenceContent.remove(NAMESPACE)
                }
                replaceType(referenceContent, localReferences, rootNamespace)
            }

            is JSONArray -> JSONArray(type.map { replaceType(it, localReferences, rootNamespace) })

            is JSONObject -> {
                type.keySet()
                    .filter { keysToUpdate.contains(it) }
                    .forEach { typeName ->
                        val name = type.getString(NAME)
                        if (referenceUsageCount.getOrDefault(type.get(typeName), 0) > 0) {
                            type.put(
                                typeName,
                                replaceUsedType(type.getString(typeName), localReferences, rootNamespace)
                            )
                        } else {
                            type.put(typeName, replaceType(type.get(typeName), localReferences, rootNamespace))
                        }
                    }
                return type
            }

            else -> type
        }
    }

    private fun replaceUsedType(
        typeName: String,
        localReferences: List<LocalReference>,
        rootNamespace: String
    ): String {
        // Should not throw as it was checked before entering this method
        val localReference = localReferences.first { it.name == typeName }
        val nameSpaceToAppend = localReference
            .getNamespace()
            .let { if (it == rootNamespace) "" else "$it." }
        return "${nameSpaceToAppend}${typeName}"
    }

    private fun LocalReference.getNamespace(): String {
        val content = JSONObject(content(rootDir))
        return if (content.has(NAMESPACE)) {
            content.getString(NAMESPACE)
        } else {
            ""
        }
    }
}