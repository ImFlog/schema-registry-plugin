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

private const val ARRAY = "array"

private const val NAME = "name"

private const val NAMESPACE = "namespace"

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
        val rootNamespace = if (rootObj.has(NAMESPACE)) {
            rootObj.getString(NAMESPACE)
        } else {
            ""
        }
        return replaceTypeReference(rootObj, localReferences, rootNamespace).toString()
    }

    private fun replaceTypeReference(type: Any, localReferences: List<LocalReference>, rootNamespace: String): Any {
        return when (type) {
            is String -> {
                val matchedReference = localReferences.firstOrNull { it.name == type } ?: return type
                val currentReferenceCount = referenceUsageCount.getOrDefault(matchedReference.name, 0).inc()
                referenceUsageCount[matchedReference.name] = currentReferenceCount
                val referenceContent = JSONObject(matchedReference.content(rootDir))
                if (matchedReference.getNamespace() == rootNamespace) {
                    referenceContent.remove(NAMESPACE)
                }
                replaceTypeReference(referenceContent, localReferences, rootNamespace)
            }

            is JSONArray -> JSONArray(
                type.map {
                    replaceTypeReference(it, localReferences, rootNamespace)
                }
            )

            is JSONObject -> {
                if (type.isArrayReference()) {
                    return defineOrReferenceArrayType(type, localReferences, rootNamespace)
                }
                return defineOrReferenceObjectType(type, localReferences, rootNamespace)
            }

            else -> type
        }
    }

    private fun defineOrReferenceArrayType(
        type: JSONObject,
        localReferences: List<LocalReference>,
        rootNamespace: String
    ): JSONObject {
        val simpleTypeName = (type.get(TYPE) as JSONObject).get(ITEMS).toString().split(".").last()
        val replacedArrayType = if (referenceUsageCount.getOrDefault(simpleTypeName, 0) > 0) {
            replaceUsedType(simpleTypeName, localReferences, rootNamespace)
        } else {
            replaceTypeReference(simpleTypeName, localReferences, rootNamespace)
        }
        return JSONObject(
            """
                   {
                    "name": "${type.get(NAME)}",
                    "type": "array",
                    "items": $replacedArrayType
                   }
                  """
        )
    }

    private fun JSONObject.isArrayReference(): Boolean {
        if (this.has(TYPE) && this.get(TYPE) is JSONObject) {
            val reference = this.get(TYPE) as JSONObject
            return reference.has(TYPE) && reference.get(TYPE) == ARRAY
        }
        return false
    }

    private fun defineOrReferenceObjectType(
        type: JSONObject,
        localReferences: List<LocalReference>,
        rootNamespace: String
    ): Any {
        type.keySet()
            .filter { keysToUpdate.contains(it) }
            .forEach { typeName ->
                if (referenceUsageCount.getOrDefault(type.get(typeName), 0) > 0) {
                    type.put(
                        typeName,
                        replaceUsedType(type.getString(typeName), localReferences, rootNamespace)
                    )
                } else {
                    type.put(typeName, replaceTypeReference(type.get(typeName), localReferences, rootNamespace))
                }
            }
        return type
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