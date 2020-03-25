package com.github.imflog.schema.registry.config

import com.github.imflog.schema.registry.RegistryClientWrapper
import io.confluent.kafka.schemaregistry.avro.AvroCompatibilityLevel
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject


open class ConfigTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        group = "registry"
        description = "Set subject compatibility in registry"
    }

    companion object {
        const val TASK_NAME = "configSubjectsTask"
    }

    @Input
    val url: Property<String> = objects.property(String::class.java)

    @Input
    val basicAuth: Property<String> = objects.property(String::class.java)

    @Input
    val subjects: ListProperty<ConfigSubject> = objects.listProperty(ConfigSubject::class.java)

    @TaskAction
    fun configureSubjects() {
        val errorCount = ConfigTaskAction(
            RegistryClientWrapper.client(url.get(), basicAuth.get()),
            subjects.get()
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount subject configuration not set, see logs for details", Throwable())
        }
    }
}
