package com.github.imflog.gradle.schema

import org.gradle.api.Project
import org.gradle.api.provider.Property

open class SchemaRegistryExtension(project: Project) {

    var url: Property<String> = project.objects.property(String::class.java)
    var output: Property<String> = project.objects.property(String::class.java)
    val subjects: Property<List<String>> = project.objects.listProperty(String::class.java)

    init {
        this.url.set("http://localhost:8081")
        this.output.set("src/main/avro")
        this.subjects.set(ArrayList())
    }
}
