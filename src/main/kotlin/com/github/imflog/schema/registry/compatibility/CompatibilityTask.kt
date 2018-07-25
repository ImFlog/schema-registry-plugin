package com.github.imflog.schema.registry.compatibility

import com.github.imflog.schema.registry.RegistryClientWrapper
import com.github.imflog.schema.registry.StringToFileSubject
import org.apache.avro.Schema
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

const val TEST_SCHEMAS_TASK = "testSchemasTask"

open class CompatibilityTask : DefaultTask() {

    @Input
    var url: Property<String> = project.objects.property(String::class.java)
    @Input
    var subjects: ListProperty<StringToFileSubject> = project.objects.listProperty(StringToFileSubject::class.java)

    @TaskAction
    fun testCompatibility() {
        var errorCount = 0
        for (subject in subjects.get()) {
            val subjectPair = lookupSchemas(subject)
            val compatible = testCompatibility(subjectPair)
            if (compatible) {
                logger.info("Schema ${subject.path} is compatible with subject(${subjectPair.first})")
            } else {
                logger.error("Schema ${subject.path} is not compatible with subject(${subject.subject})")
                errorCount++
            }
        }
        if (errorCount > 0) {
            throw CompatibilityException("$errorCount schemas are not compatible, see logs for details")
        }
    }

    private fun lookupSchemas(subject: StringToFileSubject): Pair<String, Schema> {
        logger.debug("Loading schema for subject(${subject.subject}) from ${subject.path}.")
        val schemaContent = File(project.rootDir, subject.path).readText()
        val parser = Schema.Parser()
        val parsedSchema = parser.parse(schemaContent)
        return Pair(subject.subject, parsedSchema)
    }

    private fun testCompatibility(subjectPair: Pair<String, Schema>): Boolean {
        val registryClient = RegistryClientWrapper.instance.client(url.get())
        return registryClient!!.testCompatibility(subjectPair.first, subjectPair.second)
    }
}