package com.github.imflog.schema.registry.download

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

open class DownloadExtension(project: Project) {

    var output: Property<String> = project.objects.property(String::class.java)
    val subjects: ListProperty<String> = project.objects.listProperty(String::class.java)

    init {
        this.output.set("src/main/avro")
    }
}