package com.github.imflog.schema.registry

open class SchemaRegistryBasicAuthExtension {

    var username: String? = null
    var password: String? = null

    fun getBasicAuthCredentials() = "${this.username}:${this.password}"
}
