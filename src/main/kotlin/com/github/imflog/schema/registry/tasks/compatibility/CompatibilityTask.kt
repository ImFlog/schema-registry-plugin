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
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject


abstract class CompatibilityTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        group = "registry"
        description = "Test compatibility against registry"
    }

    companion object {
        const val TASK_NAME = "testSchemasTask"
    }

    @get:InputFiles
    val inputFiles: List<File>
        get() = subjects.get().flatMap { subject ->
            val files = mutableListOf(rootDir.get().resolve(subject.file))
            subject.localReferences.forEach { files.add(rootDir.get().resolve(it.path)) }
            files
        }

    @get:OutputFile
    @get:Optional
    val dummyOutputFile: File?
        get() = if (subjects.get().isNotEmpty()) {
            rootDir.get().resolve("build/tmp/$name/dummy")
        } else null

    @Input
    val url: Property<String> = objects.property(String::class.java)

    @Input
    val clientConfig: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    @Input
    val subjects: ListProperty<Subject> = objects.listProperty(Subject::class.java)

    @Input
    val failFast: Property<Boolean> = objects.property(Boolean::class.java)

    @Input
    val rootDir: Property<File> = objects.property(File::class.java)

    @TaskAction
    fun testCompatibility() {
        val errorCount = CompatibilityTaskAction(
            RegistryClientWrapper.client(url.get(), clientConfig.get()),
            rootDir.get(),
            subjects.get(),
            failFast.getOrElse(false)
        ).run()
        if (errorCount > 0) {
            throw GradleScriptException("$errorCount schemas not compatible, see logs for details.", Throwable())
        }
        dummyOutputFile?.parentFile?.mkdirs()
        dummyOutputFile?.createNewFile()
    }
}
