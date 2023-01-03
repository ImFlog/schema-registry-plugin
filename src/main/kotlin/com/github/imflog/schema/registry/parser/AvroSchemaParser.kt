package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.apache.avro.Schema
import java.io.File

class AvroSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {

    override val schemaType: SchemaType = SchemaType.AVRO
    override fun resolveLocalAndRemoteReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>,
        remoteReferences: List<SchemaReference>
    ): String {
        val parser = Schema.Parser()

        resolveRemoteReferences(parser,remoteReferences)

        localReferences.map { reference -> reference.content(rootDir) }.reversed().forEach { parser.parse(it) }

        return parser.parse(schemaContent).toString()
    }

    fun resolveRemoteReferences(parser: Schema.Parser, remoteReferences: List<SchemaReference>){
        remoteReferences.map{
            val schema = client.getByVersion(it.subject,it.version,false)
            resolveRemoteReferences(parser, schema.references)
            schema
        }.forEach{ parser.parse(it.schema)}
    }
}