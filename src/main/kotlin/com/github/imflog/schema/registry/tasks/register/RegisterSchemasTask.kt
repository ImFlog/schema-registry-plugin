package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.RegistryClientWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class RegisterSchemasTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {

    companion object {
        const val TASK_NAME = "registerSchemasTask"
    }

    init {
        group = "registry"
        description = "Register schemas in the registry"
    }

    @Input
    val url: Property<String> = objects.property(String::class.java)

    @Input
    val basicAuth: Property<String> = objects.property(String::class.java)

    @Input
    val ssl: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    @Input
    val subjects: ListProperty<RegisterSubject> = objects.listProperty(RegisterSubject::class.java)

    @Input
    @Optional
    val outputDirectory: Property<String> = objects.property(String::class.java)

    @TaskAction
    fun registerSchemas() {
        val errorCount = RegisterTaskAction(
            RegistryClientWrapper.client(url.get(), basicAuth.get(), ssl.get()),
            project.rootDir,
            subjects.get(),
            outputDirectory.orNull
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not registered, see logs for details", Throwable())
        }
    }
}
