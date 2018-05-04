package com.github.imflog.gradle.schema

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

// TODO : fix this
@Ignore
class SchemaRegistryPluginTest {

    lateinit var project: Project

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply(SchemaRegistryPlugin::class.java)
    }

    @Test
    fun `plugin should add tasks to build when applied`() {
        project.configurations.create("build")
        val buildTask = project.tasks.getByName("build")
        assertThat(buildTask.taskDependencies.getDependencies(buildTask))
                .contains(project.tasks.getByName("downloadSchemaTask"))
    }
}