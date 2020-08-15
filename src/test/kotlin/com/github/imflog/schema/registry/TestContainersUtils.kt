package com.github.imflog.schema.registry

import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network


abstract class TestContainersUtils {

    companion object {
        private const val CONFLUENT_VERSION = "5.5.1"
        private const val KAFKA_NETWORK_ALIAS = "kafka"
        private const val SCHEMA_REGISTRY_INTERNAL_PORT = 8081

        private val network: Network = Network.newNetwork()
        private val kafkaContainer: KafkaContainer by lazy {
            KafkaContainer(CONFLUENT_VERSION)
                .withNetwork(network)
                .withNetworkAliases(KAFKA_NETWORK_ALIAS)
        }
        val schemaRegistryContainer: KGenericContainer by lazy {
            KGenericContainer("confluentinc/cp-schema-registry:5.5.1")
                .withNetwork(network)
                .withExposedPorts(SCHEMA_REGISTRY_INTERNAL_PORT)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://${KAFKA_NETWORK_ALIAS}:9092")
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
        }
        // TODO: Create the schema registry container with authentication

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            kafkaContainer.start()
            schemaRegistryContainer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
            schemaRegistryContainer.stop()
            kafkaContainer.stop()
        }
    }

    @AfterEach
    fun cleanRegistry() {
        // Sort desc is useful as the subject are referenced by other subject (ex testSubject3 use testSubject1)
        client.allSubjects.sortedDescending().forEach {subject-> client.deleteSubject(subject) }
    }

    val schemaRegistryEndpoint: String by lazy {
        val port = schemaRegistryContainer.getMappedPort(SCHEMA_REGISTRY_INTERNAL_PORT)
        "http://${schemaRegistryContainer.host}:$port"
    }
    val client by lazy {
        CachedSchemaRegistryClient(
            listOf(schemaRegistryEndpoint),
            1000,
            listOf(AvroSchemaProvider(), ProtobufSchemaProvider(), JsonSchemaProvider()),
            mapOf<String, Any>()
        )
    }
}

class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)
