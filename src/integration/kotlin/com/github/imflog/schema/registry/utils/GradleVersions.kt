package com.github.imflog.schema.registry.utils

/**
 * Gradle versions the integration tests run against.
 *
 * Every [org.gradle.testkit.runner.GradleRunner] based test is a `@ParameterizedClass` over these
 * two values so that both the oldest supported Gradle and the one this plugin is built with are
 * exercised.
 */
object GradleVersions {

    /** Lowest version the plugin claims to support (see README). */
    const val MINIMUM = "8.6"

    /** Must track `gradle/wrapper/gradle-wrapper.properties`. */
    const val CURRENT = "9.7.1"
}
