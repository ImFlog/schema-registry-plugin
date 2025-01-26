package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.RegistryClientWrapper
import com.github.imflog.schema.registry.Subject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject


open class CompatibilityTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        group = "registry"
        description = "Test compatibility against registry"
    }

    companion object {
        const val TASK_NAME = "testSchemasTask"
    }

    @Input
    val url: Property<String> = objects.property(String::class.java)

    @Input
    val basicAuth: Property<String> = objects.property(String::class.java)

    @Input
    val ssl: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    @Input
    val subjects: ListProperty<Subject> = objects.listProperty(Subject::class.java)

    @Input
    val failFast: Property<Boolean> = objects.property(Boolean::class.java)

    @TaskAction
    fun testCompatibility() {
        val errorCount = CompatibilityTaskAction(
            RegistryClientWrapper.client(url.get(), basicAuth.get(), ssl.get()),
            project.rootDir,
            subjects.get(),
            failFast.getOrElse(false)
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not compatible, see logs for details.", Throwable())
        }
    }
}
