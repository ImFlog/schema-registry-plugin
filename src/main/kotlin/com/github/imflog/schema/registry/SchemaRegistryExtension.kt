package com.github.imflog.schema.registry

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

open class SchemaRegistryExtension(objects: ObjectFactory) {

    companion object {
        const val EXTENSION_NAME = "schemaRegistry"
    }

    val url: Property<String> = objects.property(String::class.java).apply {
        // Default value
        convention("http://localhost:8081")
    }

    val quiet: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(false)
    }

    val outputDirectory: Property<String> = objects.property(String::class.java)

    val rootDir: Property<String> = objects.property(String::class.java)

    val pretty: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(false)
    }

    val failFast: Property<Boolean> = objects.property(Boolean::class.java).apply {
        convention(false)
    }

    val clientConfig: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).apply {
            convention(mutableMapOf())
        }
}
