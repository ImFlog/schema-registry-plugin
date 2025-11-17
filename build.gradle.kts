import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.github.imflog"
version = "2.4.2-SNAPSHOT"


plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradle.plugin-publish") version "2.0.0"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

// Dependencies versions
dependencies {
    implementation(gradleApi())
    implementation("io.confluent:kafka-schema-registry:8.1.0") {
        exclude("org.slf4j", "slf4j-log4j12")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(17) // Set Java 17 compatibility
}

java {
    withSourcesJar()
}

// Unit tests
dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.platform:junit-platform-launcher:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.1")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("io.mockk:mockk:1.14.6")
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
    integrationImplementation("org.wiremock:wiremock-standalone:3.13.2")
    integrationImplementation("org.testcontainers:kafka:1.21.3")
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
