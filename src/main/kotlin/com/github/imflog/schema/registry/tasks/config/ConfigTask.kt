package com.github.imflog.schema.registry.tasks.config

import com.github.imflog.schema.registry.RegistryClientWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject


abstract class ConfigTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        group = "registry"
        description = "Set subject compatibility in registry"
    }

    companion object {
        const val TASK_NAME = "configSubjectsTask"
    }

    @get:OutputFile
    @get:Optional
    val dummyOutputFile: File?
        get() = if (subjects.get().isNotEmpty()) {
            project.layout.buildDirectory.file("tmp/$name/dummy").get().asFile
        } else null

    @Input
    val url: Property<String> = objects.property(String::class.java)

    @Input
    val clientConfig: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    @Input
    val subjects: ListProperty<ConfigSubject> = objects.listProperty(ConfigSubject::class.java)

    @Input
    val failFast: Property<Boolean> = objects.property(Boolean::class.java)

    @TaskAction
    fun configureSubjects() {
        val errorCount = ConfigTaskAction(
            RegistryClientWrapper.client(url.get(), clientConfig.get()),
            subjects.get(),
            failFast.getOrElse(false)
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount subject configuration not set, see logs for details", Throwable())
        }
        dummyOutputFile?.parentFile?.mkdirs()
        dummyOutputFile?.createNewFile()
    }
}
