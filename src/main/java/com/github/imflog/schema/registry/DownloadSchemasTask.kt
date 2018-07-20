package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import org.apache.avro.Schema
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

const val DOWNLOAD_SCHEMA_TASK = "downloadSchemasTask"

open class DownloadSchemasTask : DefaultTask() {
    init {
        group = "registry"
        description = "Download schemas from the registry"
    }

    @OutputDirectory
    lateinit var outputDir: File

    @Input
    var outputPath: Property<String> = project.objects.property(String::class.java)
        set(path) {
            field = path
            this.outputDir = File(project.rootDir, path.get())
        }

    @Input
    var subjects: ListProperty<String> = project.objects.listProperty(String::class.java)
    @Input
    var url: Property<String> = project.objects.property(String::class.java)

    @TaskAction
    fun downloadSchemas() {
        logger.info("Start loading schemas for $subjects")
        subjects.get().forEach { subject ->
            val downloadedSchema = downloadSchema(url.get(), subject)
            writeSchemas(subject, downloadedSchema)
        }
    }

    private fun downloadSchema(url: String, subject: String): Schema {
        val registryClient = RegistryClientWrapper.instance.client(url)
        val latestSchemaMetadata: SchemaMetadata? = registryClient?.getLatestSchemaMetadata(subject)
        val parser = Schema.Parser()
        return parser.parse(latestSchemaMetadata!!.schema)
    }

    private fun writeSchemas(subject: String, schemas: Schema) {
        val outputFile = File(outputDir, "$subject.avsc")
        logger.info("Writing file  $outputFile")
        println("Writing file  $outputFile")
        outputFile.printWriter().use { out ->
            out.println(schemas.toString(true))
        }
    }
}