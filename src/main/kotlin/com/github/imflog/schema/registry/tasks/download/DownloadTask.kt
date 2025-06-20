package com.github.imflog.schema.registry.tasks.download

import com.github.imflog.schema.registry.RegistryClientWrapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
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
    val metadataConfig: Property<MetadataExtension> = objects.property(MetadataExtension::class.java)

    @Input
    val url: Property<String> = objects.property(String::class.java)

    @Input
    val subjects: ListProperty<DownloadSubject> = objects.listProperty(DownloadSubject::class.java)

    @Input
    val clientConfig: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    @Input
    val pretty: Property<Boolean> = objects.property(Boolean::class.java)

    @Input
    val failFast: Property<Boolean> = objects.property(Boolean::class.java)

    @Input
    val rootDir: Property<File> = objects.property(File::class.java)

    @TaskAction
    fun downloadSchemas() {
        val errorCount = DownloadTaskAction(
            RegistryClientWrapper.client(url.get(), clientConfig.get()),
            rootDir.get(),
            subjects.get(),
            metadataConfig.get(),
            pretty.get(),
            failFast.getOrElse(false),
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not downloaded, see logs for details", Throwable())
        }
    }
}
