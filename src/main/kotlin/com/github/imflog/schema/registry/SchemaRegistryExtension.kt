package com.github.imflog.schema.registry

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

open class SchemaRegistryExtension(objects: ObjectFactory) {

    val url: Property<String> = objects.property(String::class.java).apply {
        // Default value
        convention("http://localhost:8081")
    }
}
