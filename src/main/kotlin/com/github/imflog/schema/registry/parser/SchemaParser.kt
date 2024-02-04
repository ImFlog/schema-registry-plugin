package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaParsingException
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.Subject
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.Metadata
import io.confluent.kafka.schemaregistry.client.rest.entities.RuleSet
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File

abstract class SchemaParser(
    protected val client: SchemaRegistryClient,
    protected val rootDir: File
) {
    abstract val schemaType: SchemaType

    companion object {
        /**
         * This is like a factory for our local parsers.
         * We can afford to recreate the parser each time as it's not a long-running processes.
         */
        fun provide(schemaType: SchemaType, client: SchemaRegistryClient, rootDir: File): SchemaParser =
            when (schemaType) {
                SchemaType.AVRO -> AvroSchemaParser(client, rootDir)
                SchemaType.JSON -> JsonSchemaParser(client, rootDir)
                SchemaType.PROTOBUF -> ProtobufSchemaParser(client, rootDir)
            }
    }

    @Throws(SchemaParsingException::class, NotImplementedError::class)
    fun parseSchemaFromFile(
        subject: Subject
    ): ParsedSchema {
        val schemaContent = rootDir.resolve(subject.file).readText()
        val parsedLocalSchemaString = if (subject.localReferences.isNotEmpty()) {
            resolveLocalReferences(subject.inputSubject, schemaContent, subject.localReferences)
        } else schemaContent

        return client
            .parseSchema(schemaType.registryType, parsedLocalSchemaString, subject.references, subject.metadata,subject.ruleSet)
            .orElseThrow { SchemaParsingException(subject.inputSubject, schemaType) }
    }

    abstract fun resolveLocalReferences(
        subject: String,
        schemaContent: String,
        localReferences: List<LocalReference>
    ): String
}

