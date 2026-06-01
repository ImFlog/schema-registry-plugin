package com.github.imflog.schema.registry

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistryClientWrapperTest {
    @Test
    fun `buildHeaders should only return header keys from config`() {
        // Given
        val config = mapOf(
            "somekey" to "somevalue",
            "request.header" to "not a real header",
            "request.header." to "also not a real header",
            "request.header.x-api-key" to "abc123"
        )

        // When
        val headers = RegistryClientWrapper.buildHeaders(config)

        // Then
        Assertions.assertThat(headers).isNotNull
        Assertions.assertThat(headers!!.size).isEqualTo(1)
        Assertions.assertThat(headers.containsKey("x-api-key")).isTrue
        Assertions.assertThat(headers["x-api-key"]).isEqualTo("abc123")
    }

    @Test
    fun `buildHeaders should return null when config contains no headers`() {
        // Given
        val config = mapOf(
            "somekey" to "somevalue",
            "request.header" to "not a real header",
            "request.header." to "also not a real header"
        )

        // When
        val headers = RegistryClientWrapper.buildHeaders(config)

        // Then
        Assertions.assertThat(headers).isNull()
    }

    @Test
    fun `buildHeaders should return null for empty config`() {
        // Given
        val config: Map<String, String> = emptyMap()

        // When
        val headers = RegistryClientWrapper.buildHeaders(config)

        // Then
        Assertions.assertThat(headers).isNull()
    }
}
