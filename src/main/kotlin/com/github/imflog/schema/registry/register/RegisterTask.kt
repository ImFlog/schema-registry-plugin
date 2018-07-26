package com.github.imflog.schema.registry.register

import com.github.imflog.schema.registry.RegistryClientWrapper
import com.github.imflog.schema.registry.StringToFileSubject
import org.apache.avro.Schema
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

const val REGISTER_SCHEMAS_TASK = "registerSchemasTask"

open class RegisterSchemasTask : DefaultTask() {
    init {
        group = "registry"
        description = "Register schemas in the registry"
    }

    @Input
    var url: Property<String> = project.objects.property(String::class.java)

    @Input
    var subjects: ListProperty<StringToFileSubject> = project.objects.listProperty(StringToFileSubject::class.java)

    @TaskAction
    fun registerSchemas() {
        subjects.get().forEach { subject ->
            registerSchema(subject, url.get())
        }
    }

    private fun registerSchema(stringToFileSubject: StringToFileSubject, url: String) {
        val registryClient = RegistryClientWrapper.instance.client(url)
        val schema = readSchema(stringToFileSubject.path)
        logger.debug("Calling register (${stringToFileSubject.subject}, ${stringToFileSubject.path})")
        registryClient!!.register(stringToFileSubject.subject, schema)
    }

    private fun readSchema(path: String): Schema {
        val parser = Schema.Parser()
        val schemaContent = File(project.rootDir, path).readText()
        return parser.parse(schemaContent)
    }
}