package com.github.imflog.schema.registry

import org.gradle.api.Project
import org.gradle.api.provider.Property

open class SchemaRegistryExtension(project: Project) {

    var url: Property<String> = project.objects.property(String::class.java)

    init {
        this.url.set("http://localhost:8081")
    }
}
