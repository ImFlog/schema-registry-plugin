package com.github.imflog.schema.registry

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

open class BasicAuthExtension(private val objects: ObjectFactory) {

    val username: Property<String> = objects.property(String::class.java).apply {
        convention("")
    }
    val password: Property<String> = objects.property(String::class.java).apply {
        convention("")
    }

    val basicAuth: Provider<String> =
        username.flatMap { usernameStr -> password.map { passwordStr -> "$usernameStr:$passwordStr" } }
}
