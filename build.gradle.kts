import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.github.imflog"
version = "1.2.0"

plugins {
    kotlin("jvm") version "1.4.31"
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.13.0"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.38.0"
}

repositories {
    jcenter()
    mavenCentral()
    maven("http://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

java {
    withSourcesJar()
}

// Dependencies versions
val confluentVersion = "6.1.0"
dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(platform("io.confluent:kafka-schema-registry-parent:$confluentVersion"))
    implementation("io.confluent", "kafka-schema-registry") {
        exclude("org.slf4j", "slf4j-log4j12")
    }
    implementation("io.confluent", "kafka-json-schema-provider")
    implementation("io.confluent", "kafka-protobuf-provider")
}

// Test versions
val junitVersion = "5.7.1"
val mockkVersion = "1.10.6"
val wiremockVersion = "2.27.2"
val assertJVersion = "3.19.0"
val testContainersVersion = "1.15.2"
val awaitabilityVersion = "4.0.3"
dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testImplementation("org.assertj", "assertj-core", assertJVersion)
    testImplementation("io.mockk", "mockk", mockkVersion)
    testImplementation("com.github.tomakehurst", "wiremock-jre8", wiremockVersion)
    testImplementation("org.testcontainers", "kafka", testContainersVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
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
