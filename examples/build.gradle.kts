buildscript {
    repositories {
        jcenter()
        maven {
            url = uri("http://packages.confluent.io/maven/")
        }
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

plugins {
    // Set it to false to let subproject apply the plugin
    id("com.github.imflog.kafka-schema-registry-gradle-plugin") version "1.2.0" apply false
    id("com.avast.gradle.docker-compose") version "0.13.3" apply true
}

subprojects {
    apply(plugin = "docker-compose")

    val currentProject = this
    // Creates a run task for all the project
    val runTask = tasks.register("run") {
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
        useComposeFiles = listOf("${project.rootDir}/docker-compose.yml")
        captureContainersOutput = false
        stopContainers = true
        removeContainers = true
        removeVolumes = true
        removeOrphans = true
        forceRecreate = true
        projectName = project.name
    }
}
