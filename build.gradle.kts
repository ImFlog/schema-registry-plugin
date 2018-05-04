import org.gradle.wrapper.WrapperExecutor

group = "com.github.imflog"
version = "0.1.0-SNAPSHOT"

task<Wrapper>("wrapper") {
    gradleVersion = "4.4"
    distributionType = Wrapper.DistributionType.ALL
}

plugins {
    kotlin("jvm").version("1.2.41")
    id("java-gradle-plugin")
}

val kotlinVersion: String? by extra {
    buildscript.configurations["classpath"]
            .resolvedConfiguration.firstLevelModuleDependencies
            .find { it.moduleName == "org.jetbrains.kotlin.jvm.gradle.plugin" }?.moduleVersion
}

repositories {
    mavenCentral()
    maven("http://packages.confluent.io/maven/")
}

dependencies {
    compileOnly(gradleApi())
    implementation("org.jetbrains.kotlin", "kotlin-stdlib", kotlinVersion)
    implementation("io.confluent", "kafka-avro-serializer", "3.2.1")
            .exclude("org.slf4j", "slf4j-log4j12")
    implementation("org.apache.avro", "avro", "1.8.2")

    testImplementation(gradleTestKit())
    testImplementation("junit", "junit", "4.12")
    testImplementation("org.assertj", "assertj-core", "3.6.2")
    testImplementation("org.mockito", "mockito-all", "1.10.19")
    testImplementation("org.powermock", "powermock-core", "1.7.4")
}

//configure<JavaPluginConvention> {
//    sourceCompatibility = JavaVersion.VERSION_1_8
//}

gradlePlugin {
    plugins.invoke {
        "schema-registry" {
            id = "com.github.imflog.schema-registry"
            implementationClass = "com.github.imflog.gradle.schema.SchemaRegistryPlugin"
        }
    }
}