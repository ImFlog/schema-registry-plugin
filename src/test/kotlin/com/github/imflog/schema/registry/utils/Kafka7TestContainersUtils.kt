package com.github.imflog.schema.registry.utils

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

abstract class Kafka7TestContainersUtils {

    companion object : KafkaHelper("7.2.0") {
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
}

