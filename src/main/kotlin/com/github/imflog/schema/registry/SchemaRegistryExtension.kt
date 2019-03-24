package com.github.imflog.schema.registry

import org.gradle.api.Action;

open class SchemaRegistryExtension {

    var url: String = "http://localhost:8081"

    var credentials: SchemaRegistryBasicAuth = SchemaRegistryBasicAuth()

    fun credentials(action: Action<in SchemaRegistryBasicAuth>) {
        action.execute(credentials);
    }
}
