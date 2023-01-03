package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.json.JSONObject
import java.io.File

class JsonSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.JSON

    companion object {
        private const val DEFS_NODE = "${"$"}defs"
    }

    override fun resolveLocalAndRemoteReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>,
        remoteReferences: List<SchemaReference>
    ): String {
        val jsonObj = JSONObject(schemaContent)
        val localDefNodes = JSONObject()
        localReferences.forEach { reference ->
            localDefNodes.put(
                reference.name,
                JSONObject(reference.content(rootDir))
            )
        }
        jsonObj.append(DEFS_NODE, localDefNodes)
        return jsonObj.toString()
    }

}