import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.github.imflog"
version = "1.12.1-SNAPSHOT"


plugins {
    kotlin("jvm") version "1.9.20"
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

// Dependencies versions
val confluentVersion = "7.5.2"
val avroVersion = "1.11.2"
dependencies {
    shadow(gradleApi())
    implementation(platform("io.confluent:kafka-schema-registry-parent:$confluentVersion"))
    implementation("io.confluent", "kafka-schema-registry") {
        exclude("org.slf4j", "slf4j-log4j12")
    }
    implementation("io.confluent", "kafka-json-schema-provider")
    implementation("io.confluent", "kafka-protobuf-provider")
    // Our custom avro version. See https://github.com/ImFlog/avro
    implementation("com.github.ImFlog.avro", "avro", avroVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf(
            "-Xself-upper-bound-inference"
        )
    }
}

// Unit tests
val junitVersion = "5.7.2"
val mockkVersion = "1.11.0"
val assertJVersion = "3.20.2"
dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testImplementation("org.assertj", "assertj-core", assertJVersion)
    testImplementation("io.mockk", "mockk", mockkVersion)
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

configurations["integrationImplementation"].extendsFrom(configurations.runtimeOnly.get())

val wiremockVersion = "2.28.1"
val testContainersVersion = "1.17.6"
dependencies {
    integrationImplementation("com.github.tomakehurst", "wiremock-jre8", wiremockVersion)
    integrationImplementation("org.testcontainers", "kafka", testContainersVersion)
}

task<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["integration"].output.classesDirs
    classpath = sourceSets["integration"].runtimeClasspath

    dependsOn("build")
}

// Publish plugin
tasks {
    shadowJar {
        archiveClassifier.set("")
        from(sourceSets.main.get().output)
        from(project.configurations.runtimeClasspath)
    }
}

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