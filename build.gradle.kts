group = "com.github.imflog"
version = "0.9.0"

plugins {
    kotlin("jvm").version("1.3.71")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.10.1"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.28.0"
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
val confluentVersion = "5.4.1"
val avroVersion = "1.8.2"
dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlin", "kotlin-stdlib", kotlinVersion)
    implementation("io.confluent", "kafka-schema-registry", confluentVersion)
        .exclude("org.slf4j", "slf4j-log4j12")
}

// Test versions
val junitVersion = "5.6.1"
val mockkVersion = "1.9.3"
val wiremockVersion = "2.26.3"
val assertJVersion = "3.15.0"
dependencies {
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
    plugins {
        create("schema-registry") {
            id = registryPluginName
            description = "A plugin to download, register and test schemas from a Kafka Schema Registry"
            displayName = "Kafka schema registry gradle plugin"
            version = version

            implementationClass = "com.github.imflog.schema.registry.SchemaRegistryPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/ImFlog/schema-registry-plugin"
    vcsUrl = "https://github.com/ImFlog/schema-registry-plugin.git"
    tags = listOf("schema", "registry", "schema-registry", "kafka")
}
