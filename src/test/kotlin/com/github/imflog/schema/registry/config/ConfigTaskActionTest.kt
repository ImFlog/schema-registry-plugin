package com.github.imflog.schema.registry.config

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class ConfigTaskActionTest {

    @Test
    fun `Should set subject config`() {
        // given
        val registryClient = MockSchemaRegistryClient()

        val subjects = listOf(
            Pair(
                "test",
                "FULL_TRANSITIVE"
            )
        )

        // when
        val errorCount = ConfigTaskAction(
            registryClient,
            subjects
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

}