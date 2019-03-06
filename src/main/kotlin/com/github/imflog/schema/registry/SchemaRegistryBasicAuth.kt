package com.github.imflog.schema.registry

open class SchemaRegistryBasicAuth {

    var username: String? = null
    var password: String? = null

    fun getBasicAuthCredentials(): String {
        return "${this.username}:${this.password}"
    }
}
