package com.github.imflog.schema.registry.security

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty

open class SslExtension(objects: ObjectFactory) {

    companion object {
        const val EXTENSION_NAME = "ssl"
    }

    val configs: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java).apply {
        convention(mapOf())
    }
}
