package com.github.imflog.schema.registry.compatibility

import com.github.imflog.schema.registry.RegistryClientWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
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
    val subjects: ListProperty<CompatibilitySubject> = objects.listProperty(CompatibilitySubject::class.java)

    @TaskAction
    fun testCompatibility() {
        val errorCount = CompatibilityTaskAction(
            RegistryClientWrapper.client(url.get(), basicAuth.get()),
            project.rootDir,
            subjects.get()
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not compatible, see logs for details.", Throwable())
        }
    }
}
