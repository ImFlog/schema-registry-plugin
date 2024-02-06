package com.github.imflog.schema.registry

class UnknownSchemaTypeException(schemaType: String) : Exception("Unknown schema type provider $schemaType")
class SchemaParsingException(
    subject: String,
    type: SchemaType, message: String?
) :
    Exception("Could not parse schema $subject of type ${type.registryType}" + (message?.let { ": $it" } ?: "")) {

    constructor(
        subject: String,
        type: SchemaType
    ) : this(subject, type, null)
}

class MixedReferenceException : Exception("You cannot mix local and remote references")
