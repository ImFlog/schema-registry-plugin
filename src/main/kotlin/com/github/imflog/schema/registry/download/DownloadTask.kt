package com.github.imflog.schema.registry.download

import com.github.imflog.schema.registry.RegistryClientWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject


open class DownloadTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {

    companion object {
        const val TASK_NAME = "downloadSchemasTask"
    }

    init {
        group = "registry"
        description = "Download schemas from the registry"
    }

    @Input
    val url: Property<String> = objects.property(String::class.java)

    @Input
    val subjects: ListProperty<DownloadSubject> = objects.listProperty(DownloadSubject::class.java)

    @Input
    val basicAuth: Property<String> = objects.property(String::class.java)

    @TaskAction
    fun downloadSchemas() {
        val errorCount = DownloadTaskAction(
            RegistryClientWrapper.client(url.get(), basicAuth.get()),
            project.rootDir,
            subjects.get()
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not downloaded, see logs for details", Throwable())
        }
    }
}
