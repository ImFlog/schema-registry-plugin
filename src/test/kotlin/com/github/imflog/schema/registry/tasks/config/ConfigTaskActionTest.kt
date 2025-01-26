package com.github.imflog.schema.registry.tasks.config

import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class ConfigTaskActionTest {

    @Test
    fun `Should set subject config`() {
        // given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val subjects = listOf(ConfigSubject("test", "FULL_TRANSITIVE"))

        // when
        val errorCount = ConfigTaskAction(registryClient, subjects).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should fail if avro compatibility level does not exist`() {
        // given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val subjects = listOf(ConfigSubject("test", "FOO"))

        // when
        val errorCount = ConfigTaskAction(registryClient, subjects).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(1)
    }

    @Test
    fun `Should fail silently by default`() {
        // given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val subjects = listOf(
            ConfigSubject("test", "FULL_TRANSITIVE"),
            ConfigSubject("test", "FOO"),
            ConfigSubject("test", "FULL_TRANSITIVE"),
        )

        // when
        val errorCount = ConfigTaskAction(registryClient, subjects).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(1)
    }

    @Test
    fun `Should fail fast correctly`() {
        // given
        val registryClient =
            MockSchemaRegistryClient(listOf(AvroSchemaProvider(), JsonSchemaProvider(), ProtobufSchemaProvider()))
        val subjects = listOf(
            ConfigSubject("test", "FULL_TRANSITIVE"),
            ConfigSubject("test", "FOO"),
            ConfigSubject("test", "FULL_TRANSITIVE"),
        )

        // when
        try {
            ConfigTaskAction(registryClient, subjects, failFast = true).run()
            Assertions.fail("Should have thrown an exception")
        } catch (e: IllegalArgumentException) {
            // then
            // Nothing specific to check here
        }
    }
}
