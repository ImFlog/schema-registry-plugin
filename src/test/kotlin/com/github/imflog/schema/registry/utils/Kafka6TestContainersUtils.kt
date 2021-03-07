package com.github.imflog.schema.registry.utils

import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.io.File

abstract class Kafka6TestContainersUtils {

    companion object {
        private const val DEFAULT_CONFLUENT_VERSION = "6.1.0"
        private const val KAFKA_NETWORK_ALIAS = "kafka"
        private const val SCHEMA_REGISTRY_INTERNAL_PORT = 8081

        private val network: Network = Network.newNetwork()
        private val kafkaContainer: KafkaContainer by lazy {
            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:$DEFAULT_CONFLUENT_VERSION"))
                .withNetwork(network)
                .withNetworkAliases(KAFKA_NETWORK_ALIAS)
        }
        val schemaRegistryContainer: KGenericContainer by lazy {
            KGenericContainer("confluentinc/cp-schema-registry:$DEFAULT_CONFLUENT_VERSION")
                .withNetwork(network)
                .withExposedPorts(SCHEMA_REGISTRY_INTERNAL_PORT)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://$KAFKA_NETWORK_ALIAS:9092")
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
        }
        val schemaRegistrySslContainer: KGenericContainer by lazy {
            KGenericContainer("confluentinc/cp-schema-registry:$DEFAULT_CONFLUENT_VERSION")
                .withNetwork(network)
                .withExposedPorts(SCHEMA_REGISTRY_INTERNAL_PORT)
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "https://0.0.0.0:$SCHEMA_REGISTRY_INTERNAL_PORT")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://$KAFKA_NETWORK_ALIAS:9092")
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "registry-ssl")
                .withEnv("SCHEMA_REGISTRY_SSL_KEYSTORE_LOCATION", "/etc/schema-registry/secrets/registry.keystore.jks")
                .withEnv("SCHEMA_REGISTRY_SSL_KEYSTORE_PASSWORD", "registry")
                .withEnv("SCHEMA_REGISTRY_SSL_KEY_PASSWORD", "registry")
                .withEnv(
                    "SCHEMA_REGISTRY_SSL_TRUSTSTORE_LOCATION",
                    "/etc/schema-registry/secrets/registry.truststore.jks"
                )
                .withEnv("SCHEMA_REGISTRY_SSL_TRUSTSTORE_PASSWORD", "registry")
                .withEnv("SCHEMA_REGISTRY_SCHEMA_REGISTRY_INTER_INSTANCE_PROTOCOL", "https")
                .withEnv("SCHEMA_REGISTRY_SCHEMA_REGISTRY_GROUP_ID", "schema-registry-ssl")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_TOPIC", "_ssl_schemas")
                .withEnv("SCHEMA_REGISTRY_SSL_CLIENT_AUTHENTICATION", "REQUIRED")
                .withFileSystemBind(
                    File(Kafka5TestContainersUtils::class.java.getResource("/secrets").toURI()).absolutePath,
                    "/etc/schema-registry/secrets"
                )
        }

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            kafkaContainer.start()
            schemaRegistryContainer.start()
            schemaRegistrySslContainer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
            schemaRegistryContainer.stop()
            schemaRegistrySslContainer.stop()
            kafkaContainer.stop()
        }
    }

    val schemaRegistryEndpoint: String by lazy {
        val port = schemaRegistryContainer.getMappedPort(SCHEMA_REGISTRY_INTERNAL_PORT)
        "http://${schemaRegistryContainer.host}:$port"
    }

    val schemaRegistrySslEndpoint: String by lazy {
        val port = schemaRegistrySslContainer.getMappedPort(SCHEMA_REGISTRY_INTERNAL_PORT)
        "https://${schemaRegistrySslContainer.host}:$port"
    }

    val client by lazy {
        CachedSchemaRegistryClient(
            listOf(schemaRegistryEndpoint),
            100,
            listOf(AvroSchemaProvider(), ProtobufSchemaProvider(), JsonSchemaProvider()),
            mapOf<String, Any>()
        )
    }
}
