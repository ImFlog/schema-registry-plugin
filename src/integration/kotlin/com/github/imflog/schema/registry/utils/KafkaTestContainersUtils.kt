package com.github.imflog.schema.registry.utils

import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.io.File

abstract class KafkaTestContainersUtils {

    companion object {
        private const val SCHEMA_REGISTRY_INTERNAL_PORT = 8081

        private val CONFLUENT_VERSION = System.getenv().getOrDefault("KAFKA_VERSION", "7.6.0")
        private val KAFKA_NETWORK_ALIAS = "kafka-${CONFLUENT_VERSION}"

        private val network: Network = Network.newNetwork()
        private val kafkaContainer: ConfluentKafkaContainer by lazy {
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:$CONFLUENT_VERSION"))
                .withNetwork(network)
                .withNetworkAliases(KAFKA_NETWORK_ALIAS)
        }
        val schemaRegistryContainer: GenericContainer<*> by lazy {
            GenericContainer("confluentinc/cp-schema-registry:$CONFLUENT_VERSION")
                .withNetwork(network)
                .withExposedPorts(SCHEMA_REGISTRY_INTERNAL_PORT)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://$KAFKA_NETWORK_ALIAS:9093")
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
        }

        val schemaRegistrySslContainer: GenericContainer<*> by lazy {
            GenericContainer("confluentinc/cp-schema-registry:$CONFLUENT_VERSION")
                .withNetwork(network)
                .withExposedPorts(SCHEMA_REGISTRY_INTERNAL_PORT)
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "https://0.0.0.0:$SCHEMA_REGISTRY_INTERNAL_PORT")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://$KAFKA_NETWORK_ALIAS:9093")
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
                    File(KafkaTestContainersUtils::class.java.getResource("/secrets").toURI()).absolutePath,
                    "/etc/schema-registry/secrets",
                    BindMode.READ_ONLY
                )
        }

        init {
            kafkaContainer.start()
            schemaRegistryContainer.start()
            schemaRegistrySslContainer.start()
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