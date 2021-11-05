package com.github.imflog.schema.registry

class UnknownSchemaTypeException(schemaType: String) : Exception("Unknown schema type provider $schemaType")
class SchemaParsingException(subject: String, type: SchemaType) :
    Exception("Could not parse schema $subject of type ${type.registryType}")
