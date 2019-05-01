group = "com.github.imflog"
version = "0.6.0-SNAPSHOT"

plugins {
    kotlin("jvm").version("1.3.31")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.10.1"
}

val kotlinVersion: String? by extra {
    buildscript.configurations["classpath"]
            .resolvedConfiguration.firstLevelModuleDependencies
            .find { it.moduleName == "org.jetbrains.kotlin.jvm.gradle.plugin" }?.moduleVersion
}
repositories {
    jcenter()
    mavenCentral()
    maven("http://packages.confluent.io/maven/")
}

// Dependencies versions
val confluentVersion = "5.0.0"
val avroVersion = "1.8.2"

// Test versions
val junitVersion = "5.4.2"
val mockkVersion = "1.9"
val wiremockVersion = "2.23.2"
val assertJVersion = "3.12.2"

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlin", "kotlin-stdlib", kotlinVersion)
    implementation("io.confluent", "kafka-schema-registry", confluentVersion)
            .exclude("org.slf4j", "slf4j-log4j12")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testRuntime("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
    testImplementation("org.assertj", "assertj-core", assertJVersion)
    testImplementation("io.mockk", "mockk", mockkVersion)
    testImplementation("com.github.tomakehurst", "wiremock-jre8", wiremockVersion)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val registryPluginName = "com.github.imflog.kafka-schema-registry-gradle-plugin"
gradlePlugin {
    plugins.invoke {
        create("schema-registry") {
            id = registryPluginName
            implementationClass = "com.github.imflog.schema.registry.SchemaRegistryPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/ImFlog/schema-registry-plugin"
    vcsUrl = "https://github.com/ImFlog/schema-registry-plugin.git"

    (plugins) {
        create("schemaRegistryPlugin") {
            id = registryPluginName
            description = "A plugin to download, register and test schemas from a Kafka Schema Registry"
            displayName = "Kafka schema registry gradle plugin"
            tags = listOf("schema", "registry", "schema-registry", "kafka")
            version = version
        }
    }
}
