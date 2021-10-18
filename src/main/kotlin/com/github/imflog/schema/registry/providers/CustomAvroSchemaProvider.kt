package com.github.imflog.schema.registry.providers

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File
import java.lang.Exception
import java.util.Optional
import org.slf4j.LoggerFactory

class CustomAvroSchemaProvider : AvroSchemaProvider() {
    private val log = LoggerFactory.getLogger(CustomAvroSchemaProvider::class.java)

    private val AVRO_VALIDATE_DEFAULTS = "avro.validate.defaults"

    private var validateDefaults = false

    override fun configure(configs: Map<String?, *>) {
        super.configure(configs)
        val validate = configs[AVRO_VALIDATE_DEFAULTS] as String?
        validateDefaults = validate.toBoolean()
    }

    override fun parseSchema(
        schemaString: String,
        references: List<SchemaReference>,
        isNew: Boolean
    ): Optional<ParsedSchema> {
        // TODO: Put the version in a global const
        val (localReferences, remoteReferences) = references.partition { it.version == -2 }

        // TODO: Put the path in the name (kinda ugly ...)
        val resolvedReferences = localReferences
            .associate { reference -> reference.subject to File(reference.name).readText() }
            .plus(resolveReferences(remoteReferences))
        return try {
            Optional.of(
                AvroSchema(
                    schemaString,
                    remoteReferences,
                    resolvedReferences,
                    null,
                    validateDefaults && isNew
                )
            )
        } catch (e: Exception) {
            log.error("Could not parse Avro schema", e)
            Optional.empty()
        }
    }
}
