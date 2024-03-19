buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.github.imflog:kafka-schema-registry-gradle-plugin:2.1.0")
    }
}

plugins {
    id("com.avast.gradle.docker-compose") version "0.17.6" apply true
}

subprojects {
    apply(plugin = "docker-compose")

    val currentProject = this
    // Creates a run task for all the project
    tasks.register("run") {
        this.group = "build"
        val register = currentProject.tasks.getByName("registerSchemasTask")
        val configure = currentProject.tasks.getByName("configSubjectsTask")
        val download = currentProject.tasks.getByName("downloadSchemasTask")
        val test = currentProject.tasks.getByName("testSchemasTask")
        dependsOn(register, configure, download, test)
        configure.mustRunAfter(register)
        download.mustRunAfter(configure)
        test.mustRunAfter(download)

        finalizedBy(currentProject.tasks.getByName("composeDown"))
        dependsOn(currentProject.tasks.getByName("composeUp"))
    }

    dockerCompose {
        useComposeFiles.addAll(listOf("${project.rootDir}/docker-compose.yml"))
        captureContainersOutput.set(false)
        stopContainers.set(true)
        removeContainers.set(true)
        removeVolumes.set(true)
        removeOrphans.set(true)
        forceRecreate.set(true)
        setProjectName(project.name)
    }
}
