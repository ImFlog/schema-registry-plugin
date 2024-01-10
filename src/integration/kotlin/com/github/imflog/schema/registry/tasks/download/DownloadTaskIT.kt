package com.github.imflog.schema.registry.tasks.download

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.toSchemaType
import com.github.imflog.schema.registry.utils.KafkaTestContainersUtils
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import org.apache.avro.Schema
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.File
import java.util.stream.Stream

class DownloadTaskIT : KafkaTestContainersUtils() {

    private lateinit var folderRule: TemporaryFolder
    private lateinit var buildFile: File
    private val objectMapper = ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @BeforeEach
    fun init() {
        folderRule = TemporaryFolder()
        folderRule.create()
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
        client.reset()
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(SchemaArgumentProvider::class)
    fun `Should download schemas`(type: String, oldSchema: ParsedSchema, newSchema: ParsedSchema) {
        // Given
        val subjectName = "parameterized-$type"

        client.register(subjectName, oldSchema)
        client.register(subjectName, newSchema)

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subject('$subjectName', '${folderRule.root.absolutePath}/src/main/$type/test')
                    subject('$subjectName', 'src/main/$type/test_v1', 1)
                    subject('$subjectName', 'src/main/$type/test_v2', 2)
                }
            }
        """
        )

        // When
        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        val schemaFile = "$subjectName.${oldSchema.schemaType().toSchemaType().extension}"
        Assertions.assertThat(File(folderRule.root, "src/main/$type/test")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/$type/test/$schemaFile")).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        Assertions.assertThat(File(folderRule.root, "src/main/$type/test_v1")).exists()
        val resultFile1 = File(folderRule.root, "src/main/$type/test_v1/$schemaFile")
        Assertions.assertThat(resultFile1).exists()
        Assertions.assertThat(resultFile1.readText()).doesNotContain("description")

        Assertions.assertThat(File(folderRule.root, "src/main/$type/test_v2")).exists()
        val resultFile2 = File(folderRule.root, "src/main/$type/test_v2/$schemaFile")
        Assertions.assertThat(resultFile2).exists()
        Assertions.assertThat(resultFile2.readText()).contains("description")
    }

    @Test
    fun `Should format supported schema types when pretty is specified`() {

        val type = AvroSchema.TYPE
        val schema =  AvroSchema(
                """{
                            "type": "record",
                            "name": "User",
                            "fields": [
                                { "name": "name", "type": "string" }, 
                                { "name": "description", "type": ["null", "string"], "default": null }
                            ]
                        }""".filter { !it.isWhitespace() }
        )

        // Given
        val subjectName = "prettyprinted-$type"

        client.register(subjectName, schema)

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                pretty = true
                download {
                    subject('$subjectName', '${folderRule.root.absolutePath}/src/main/$type/test')
                }
            }
        """
        )

        // When
        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("7.6")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        val schemaType = schema.schemaType().toSchemaType()
        val schemaFile = "$subjectName.${schemaType.extension}"

        Assertions.assertThat(File(folderRule.root, "src/main/$type/test")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/$type/test/$schemaFile")).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        Assertions.assertThat(File(folderRule.root, "src/main/$type/test/$schemaFile")).hasContent(
            objectMapper.readTree(schema.toString()).toPrettyString()
        )
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(SchemaArgumentProvider::class)
    fun `Should download schemas by subject name pattern`(
        type: String,
        oldSchema: ParsedSchema,
        newSchema: ParsedSchema
    ) {
        // Given
        val subjectName = "parameterized-$type"

        client.register(subjectName, oldSchema)
        client.register(subjectName, newSchema)

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subjectPattern('parameterized-[a-zA-Z]+', '${folderRule.root.absolutePath}/src/main/$type/test')
                }
            }
        """
        )

        // When
        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        val schemaFile = "$subjectName.${oldSchema.schemaType().toSchemaType().extension}"
        Assertions.assertThat(File(folderRule.root, "src/main/$type/test")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/$type/test/$schemaFile")).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `Should fail download when schema does not exist`() {
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subject('UNKNOWN', 'src/main/avro/test')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    fun `Should save files under a custom output name`() {
        // Given
        val subjectName = "test-user"
        val outputName = "other_output_name"

        client.register(
            subjectName,
            AvroSchema(
                """{
                    "type": "record",
                    "name": "User",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }"""
            )
        )

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    subject('$subjectName', '${folderRule.root.absolutePath}/src/main/avro/test', "$outputName")
                }
            }
        """
        )

        // When
        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        val schemaFile = "$outputName.avsc"
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test/$schemaFile")).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `Should download schema metadata in default directory`() {
        // Given
        val subjectName = "test"

        client.register(
            subjectName,
            AvroSchema(
                """{
                    "type": "record",
                    "name": "User",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }"""
            )
        )

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            import com.github.imflog.schema.registry.tasks.download.MetadataExtension
            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    metadata = new MetadataExtension(true)
                    subject('$subjectName', '${folderRule.root.absolutePath}/src/main/avro/test')
                }
            }
        """
        )

        // When
        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        val schemaFile = "$subjectName.avsc"
        val metadataFile = "$subjectName-metadata.json"
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test/$schemaFile")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test/$metadataFile")).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `Should download schema metadata in specific directory`() {
        // Given
        val subjectName = "test"

        client.register(
            subjectName,
            AvroSchema(
                """{
                    "type": "record",
                    "name": "User",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }"""
            )
        )

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            import com.github.imflog.schema.registry.tasks.download.MetadataExtension
            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    metadata = new MetadataExtension(true, '${folderRule.root.absolutePath}/src/main/avro/metadata')

                    subject('$subjectName', '${folderRule.root.absolutePath}/src/main/avro/test')
                }
            }
        """
        )

        // When
        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        val schemaFile = "$subjectName.avsc"
        val metadataFile = "$subjectName-metadata.json"
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test/$schemaFile")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/metadata/$metadataFile")).exists()
        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `Should download schema with references in specific directory`() {
        // Given
        val subjectName = "reference_test"
        val recordName = "UserReference"
        val subjectNameLib = "reference_test_lib"
        val recordNameLib = "UserReferenceLib"

        client.register(
            subjectNameLib,
            AvroSchema(
                """{
                    "type": "record",
                    "name": "${recordNameLib}",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }"""
            )
        )

        client.register(
            subjectName,
            AvroSchema(
                """{
                    "type": "record",
                    "name": "${recordName}",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf(
                    SchemaReference(recordNameLib, subjectNameLib, 1)
                ),
                mapOf(),
                null
            )
        )

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            import com.github.imflog.schema.registry.tasks.download.MetadataExtension
            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                download {
                    metadata = new MetadataExtension(true, '${folderRule.root.absolutePath}/src/main/avro/metadata')

                    subject('$subjectName', '${folderRule.root.absolutePath}/src/main/avro/test', true)
                }
            }
        """
        )

        // When
        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(DownloadTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        // Then
        val schemaFile = "$subjectName.avsc"
        val metadataFile = "$subjectName-metadata.json"
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test/$schemaFile")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/metadata/$metadataFile")).exists()

        val libSchemaFile = "$subjectNameLib.avsc"
        val libMetadataFile = "$subjectNameLib-metadata.json"
        Assertions.assertThat(File(folderRule.root, "src/main/avro/test/$libSchemaFile")).exists()
        Assertions.assertThat(File(folderRule.root, "src/main/avro/metadata/$libMetadataFile")).exists()

        Assertions.assertThat(result?.task(":downloadSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    private class SchemaArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> =
            Stream.of(
                Arguments.of(
                    AvroSchema.TYPE,
                    AvroSchema(
                        """{
                        "type": "record",
                        "name": "User",
                        "fields": [
                            { "name": "name", "type": "string" }
                        ]
                    }"""
                    ),
                    AvroSchema(
                        """{
                        "type": "record",
                        "name": "User",
                        "fields": [
                            { "name": "name", "type": "string" }, 
                            { "name": "description", "type": ["null", "string"], "default": null }
                        ]
                    }"""
                    )
                ),
                Arguments.of(
                    JsonSchema.TYPE,
                    JsonSchema(
                        """{
                        "properties": {
                            "name": {"type": "string"}
                        },
                        "additionalProperties": false
                    }"""
                    ),
                    JsonSchema(
                        """{
                        "properties": {
                            "name": {"type": "string"},
                            "description": {"type": "string"}
                        },
                        "additionalProperties": false
                    }"""
                    )
                ),

                Arguments.of(
                    ProtobufSchema.TYPE,
                    ProtobufSchema(
                        """
                        syntax = "proto3";
                        option java_outer_classname = "User";

                        message TestMessage {
                            string name = 1;
                        }
                    """
                    ),
                    ProtobufSchema(
                        """
                        syntax = "proto3";
                        option java_outer_classname = "User";
                        
                        message TestMessage {
                            string name = 1;
                            string description = 2;
                        }
                    """
                    )
                )
            )
    }
}
