group = "com.github.imflog"
version = "0.10.0-SNAPSHOT"

plugins {
    kotlin("jvm").version("1.3.72")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.12.0"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.28.0"
}

repositories {
    jcenter()
    mavenCentral()
    maven("http://packages.confluent.io/maven/")
}

java {
    withSourcesJar()
}

// Dependencies versions
val confluentVersion = "5.4.1"
val avroVersion = "1.8.2"
dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))
    implementation("io.confluent", "kafka-schema-registry", confluentVersion) {
        exclude("org.slf4j", "slf4j-log4j12")
    }
}

// Test versions
val junitVersion = "5.6.2"
val mockkVersion = "1.10.0"
val wiremockVersion = "2.27.1"
val assertJVersion = "3.16.1"
dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
    testImplementation("org.assertj", "assertj-core", assertJVersion)
    testImplementation("io.mockk", "mockk", mockkVersion)
    testImplementation("com.github.tomakehurst", "wiremock-jre8", wiremockVersion)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ImFlog/schema-registry-plugin")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
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
