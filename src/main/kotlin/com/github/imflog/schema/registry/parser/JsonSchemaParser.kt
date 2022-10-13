package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.ParsedSchema
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

    override fun parseSchemaFromFile(
        subject: String,
        schemaPath: String,
        remoteReferences: List<SchemaReference>,
        localReferences: List<LocalReference>
    ): ParsedSchema {
        val schemaContent = rootDir.resolve(schemaPath).readText()
        val schema = resolveLocalReferences(schemaContent, localReferences)
        return client
            .parseSchema(schemaType.registryType, schema.toString(), remoteReferences)
            .orElseThrow { SchemaParsingException(subject, schemaType) }
    }

    // TODO:
    //  * Test local + remote
    //  * Create an unit test that use https://github.com/everit-org/json-schema to check the schema validity
    private fun resolveLocalReferences(
        schemaContent: String,
        localReferences: List<LocalReference>
    ): JSONObject {
        val jsonObj = JSONObject(schemaContent)
        val localDefNodes = JSONObject()
        localReferences.forEach { reference -> localDefNodes.put(reference.name, JSONObject(reference.content(rootDir))) }
        jsonObj.append(DEFS_NODE, localDefNodes)
        return jsonObj
    }
}