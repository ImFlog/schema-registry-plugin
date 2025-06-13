import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.github.imflog"
version = "2.3.3"


plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradle.plugin-publish") version "1.3.1"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

// Dependencies versions
dependencies {
    implementation(gradleApi())
    implementation("io.confluent:kafka-schema-registry:7.9.0") {
        exclude("org.slf4j", "slf4j-log4j12")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(8) // Set Java 8 compatibility
}

java {
    withSourcesJar()
}

// Unit tests
dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("io.mockk:mockk:1.14.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Integration tests
val integrationSource = sourceSets.create("integration") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val integrationImplementation: Configuration by configurations.getting {
    extendsFrom(
        configurations.implementation.get(),
        configurations.testImplementation.get()
    )
}

configurations["integrationImplementation"].extendsFrom(
    configurations.runtimeOnly.get(),
    configurations.testRuntimeOnly.get(),
    configurations.testCompileClasspath.get()
)

dependencies {
    integrationImplementation("com.github.tomakehurst:wiremock-jre8:2.35.1")
    integrationImplementation("org.testcontainers:kafka:1.20.6")
}

task<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["integration"].output.classesDirs
    classpath = sourceSets["integration"].runtimeClasspath

    dependsOn("build")
}

// Publish plugin
val registryPluginName = "com.github.imflog.kafka-schema-registry-gradle-plugin"
@Suppress("UnstableApiUsage")
gradlePlugin {
    website.set("https://github.com/ImFlog/schema-registry-plugin")
    vcsUrl.set("https://github.com/ImFlog/schema-registry-plugin.git")
    testSourceSets(
        sourceSets["test"],
        integrationSource
    )
    plugins {
        create("schema-registry") {
            id = registryPluginName
            description = "A plugin to download, register and test schemas from a Kafka Schema Registry"
            displayName = "Kafka schema registry gradle plugin"
            version = version
            tags.set(listOf("schema", "registry", "schema-registry", "kafka"))

            implementationClass = "com.github.imflog.schema.registry.SchemaRegistryPlugin"
        }
    }
}
