import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.github.imflog"
version = "1.11.1-SNAPSHOT"


plugins {
    kotlin("jvm") version "1.8.20"
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.2.0"
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/imflog/avro")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}

// Dependencies versions
val kotlinVersion = "1.8.20"
val confluentVersion = "7.3.3"
val avroVersion = "1.12.0-ImFlog"
dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib", kotlinVersion))
    implementation(kotlin("reflect", kotlinVersion))
    implementation(platform("io.confluent:kafka-schema-registry-parent:$confluentVersion"))
    implementation("io.confluent", "kafka-schema-registry") {
        exclude("org.slf4j", "slf4j-log4j12")
    }
    implementation("io.confluent", "kafka-json-schema-provider")
    implementation("io.confluent", "kafka-protobuf-provider")
    // Our custom avro version. See https://github.com/ImFlog/avro
    implementation("org.apache.avro", "avro", avroVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf(
            "-Xself-upper-bound-inference"
        )
    }
}

java {
    withSourcesJar()
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
val registryPluginName = "com.github.imflog.kafka-schema-registry-gradle-plugin"
gradlePlugin {
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

            implementationClass = "com.github.imflog.schema.registry.SchemaRegistryPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/ImFlog/schema-registry-plugin"
    vcsUrl = "https://github.com/ImFlog/schema-registry-plugin.git"
    tags = listOf("schema", "registry", "schema-registry", "kafka")
}