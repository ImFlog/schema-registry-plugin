package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AvroSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.AVRO

    override fun resolveLocalReferences(
        subject: String,
        schemaPath: String,
        localReferences: List<LocalReference>
    ): String {
        // Load and parse the main schema
        val mainSchema = JSONObject(loadContent(schemaPath))

        // Create a map of reference name to schema content
        val referenceSchemas = localReferences.associate { reference ->
            reference.name to JSONObject(reference.content(rootDir))
        }

        // Set to track which references have already been visited
        val visitedReferences = mutableSetOf<String>()

        // Process the schema recursively
        val resolvedSchema = resolveReferences(mainSchema, referenceSchemas, null, visitedReferences)
        return resolvedSchema.toString()
    }

    private fun resolveReferences(
        schema: JSONObject,
        references: Map<String, JSONObject>,
        parentNamespace: String?,
        visitedReferences: MutableSet<String>
    ): JSONObject {
        // Get the current namespace, falling back to parent namespace if not present
        val currentNamespace = schema.optString("namespace", parentNamespace)

        when (schema.opt("type")) {
            // If it's a record type, process its fields
            "record" -> {
                val fields = schema.getJSONArray("fields")
                for (i in 0 until fields.length()) {
                    val field = fields.getJSONObject(i)
                    fields.put(i, resolveReferences(field, references, currentNamespace, visitedReferences))
                }
            }
            // If it's an array type, process its items
            "array" -> {
                if (schema.opt("items") is JSONObject) {
                    schema.put(
                        "items",
                        resolveReferences(
                            schema.getJSONObject("items"),
                            references,
                            currentNamespace,
                            visitedReferences
                        )
                    )
                }
                if (schema.opt("items") is org.json.JSONArray) {
                    val items = schema.getJSONArray("items")
                    for (i in 0 until items.length()) {
                        items.put(
                            i,
                            resolveReferences(items.getJSONObject(i), references, currentNamespace, visitedReferences)
                        )
                    }
                }
                if (schema.opt("items") is String) {
                    val items = schema.getString("items")
                    val ref = handleStringRef(items, currentNamespace, references, visitedReferences)
                    schema.put("items", ref)
                }
            }
            // If it's a map type, process its values
            "map" -> {
                if (schema.opt("values") is JSONObject) {
                    schema.put(
                        "values",
                        resolveReferences(
                            schema.getJSONObject("values"),
                            references,
                            currentNamespace,
                            visitedReferences
                        )
                    )
                }
                if (schema.opt("values") is JSONArray) {
                    val values = schema.getJSONArray("values")
                    for (i in 0 until values.length()) {
                        values.put(
                            i,
                            resolveReferences(values.getJSONObject(i), references, currentNamespace, visitedReferences)
                        )
                    }
                }
                if (schema.opt("values") is String) {
                    val values = schema.getString("values")
                    val ref = handleStringRef(values, currentNamespace, references, visitedReferences)
                    schema.put("values", ref)
                }
            }
            // If it's a JsonObject type, process its properties
            is JSONObject -> {
                schema.put(
                    "type",
                    resolveReferences(schema.getJSONObject("type"), references, currentNamespace, visitedReferences)
                )
            }

            // If it's a union type, process each type in the union
            is JSONArray -> {
                val types = schema.getJSONArray("type")
                for (i in 0 until types.length()) {
                    when (val type = types.get(i)) {
                        is String -> {
                            val ref = handleStringRef(type, currentNamespace, references, visitedReferences)
                            types.put(i, ref)
                        }

                        is JSONObject -> {
                            types.put(i, resolveReferences(type, references, currentNamespace, visitedReferences))
                        }
                    }
                }
            }

            // If it's a string type reference
            is String -> {
                val ref = handleStringRef(schema.getString("type"), currentNamespace, references, visitedReferences)
                schema.put("type", ref)
            }
        }
        return schema
    }

    private fun handleStringRef(
        items: String,
        currentNamespace: String?,
        references: Map<String, JSONObject>,
        visitedReferences: MutableSet<String>
    ): Any {
        val referenceKey = findReferenceKey(items, currentNamespace, references)
        if (referenceKey != null) {
            if (!visitedReferences.contains(referenceKey)) {
                visitedReferences.add(referenceKey)
            } else {
                return items
            }
            // deep copy the reference and resolve local references in references (as local references can have references)
            val refSchemaCopy = JSONObject(references[referenceKey]!!.toString())
            val referencedSchema = resolveReferences(refSchemaCopy, references, null, visitedReferences)
            val refNamespace = referencedSchema.optString("namespace")

            if (refNamespace == currentNamespace) {
                referencedSchema.remove("namespace")
            }
            return referencedSchema
        }
        return items
    }

    private fun findReferenceKey(typeStr: String, namespace: String?, references: Map<String, JSONObject>): String? {
        // First try the type as is
        if (references.containsKey(typeStr)) {
            return typeStr
        }

        // If there's a namespace, try with the namespace
        if (namespace != null && !typeStr.contains(".")) {
            val fullyQualifiedType = "$namespace.$typeStr"
            // Check if the fully qualified name exists in references
            if (references.containsKey(fullyQualifiedType)) {
                return fullyQualifiedType
            }

            // Check if the simple name exists in references and has matching namespace
            references[typeStr]?.let { schema ->
                if (schema.optString("namespace") == namespace) {
                    return typeStr
                }
            }
        }

        // If the type already has a namespace (contains a dot), try to extract the simple name
        if (typeStr.contains(".")) {
            val simpleName = typeStr.substringAfterLast(".")
            if (references.containsKey(simpleName)) {
                return simpleName
            }
        }

        return null
    }
}
