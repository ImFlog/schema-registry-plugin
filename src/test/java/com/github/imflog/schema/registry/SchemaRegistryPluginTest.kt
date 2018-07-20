package com.github.imflog.schema.registry

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

class SchemaRegistryPluginTest {

    lateinit var project: Project

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(SchemaRegistryPlugin::class.java)
    }

    @Test
    fun `plugin should add tasks when applied`() {
        val downloadSchemaTask = project.tasks.getByName(DOWNLOAD_SCHEMA_TASK)
        assertThat(downloadSchemaTask).isNotNull()
    }
}