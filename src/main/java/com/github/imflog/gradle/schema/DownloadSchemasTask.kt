package com.github.imflog.gradle.schema

import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

open class DownloadSchemasTask : DefaultTask() {
    init {
        group = "Registry"
        description = "Download schemas from the registry"
    }

    @OutputDirectory
    lateinit var outputDir: File

    var outputPath: Property<String> = project.objects.property(String::class.java)
        set(path) {
            field = path
            this.outputDir = File(project.buildDir, path.get())
        }

    var subjects: Property<List<String>> = project.objects.listProperty(String::class.java)
    var url: Property<String> = project.objects.property(String::class.java)

    @TaskAction
    fun downloadSchemas() {
        logger.info("Start loading schemas for $subjects")
        subjects.get().forEach { it ->
            downloadSchema(url.get(), it)
        }
    }

    private fun downloadSchema(url: String, subject: String) {
        val latestSchemaMetadata: SchemaMetadata? = RegistryClientWrapper.instance.client(url)
                ?.getLatestSchemaMetadata(subject)
        writeSchemas(subject, latestSchemaMetadata?.schema!!)
    }

    private fun writeSchemas(subject: String, schemas: String) {
        val outputFile = File(outputDir, "$subject.avsc")
        logger.info("Writing file  $outputFile")
        println("Writing file  $outputFile")
        outputFile.printWriter().use { out ->
            out.println(schemas)
        }
    }
}