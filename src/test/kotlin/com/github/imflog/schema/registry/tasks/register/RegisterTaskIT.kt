package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.utils.Kafka5TestContainersUtils
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.File
import java.util.stream.Stream

class RegisterTaskIT : Kafka5TestContainersUtils() {
    private lateinit var folderRule: TemporaryFolder
    private lateinit var buildFile: File

    @BeforeEach
    fun beforeEach() {
        folderRule = TemporaryFolder()
    }

    @AfterEach
    fun afterEach() {
        folderRule.delete()
    }

    @ParameterizedTest
    @ArgumentsSource(SchemaArgumentProvider::class)
    fun `RegisterSchemasTask should register schemas`(
        type: String,
        userSchema: String,
        playerSchema: String

    ) {
        folderRule.create()
        folderRule.newFolder(type)
        val subjectName = "parameterized-$type"
        val extension = when (type) {
            AvroSchema.TYPE -> "avsc"
            ProtobufSchema.TYPE -> "proto"
            JsonSchema.TYPE -> "json"
            else -> throw Exception("Should not happen")
        }

        val userPath = "$type/user.$extension"
        val userSubject = "$subjectName-user"
        val userFile = folderRule.newFile(userPath)
        userFile.writeText(userSchema)

        val playerPath = "$type/player.$extension"
        val playerSubject = "$subjectName-player"
        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchema)

        // Small trick, for protobuf the name to import is not User but user.proto
        val referenceName = if (type == ProtobufSchema.TYPE) "user.proto" else "User"

        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = '$schemaRegistryEndpoint'
                register {
                    subject('$userSubject', '${userFile.absolutePath}', '$type')
                    subject('$playerSubject', '$playerPath', '$type').addReference('$referenceName', '$userSubject', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(RegisterSchemasTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }


    private class SchemaArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> =
            Stream.of(
                Arguments.of(
                    AvroSchema.TYPE,
                    """{
                        "type": "record",
                        "name": "User",
                        "fields": [
                            { "name": "name", "type": "string" }
                        ]
                    }""",
                    """{
                        "type": "record",
                        "name": "Player",
                        "fields": [
                            { "name": "identifier", "type": "string" }, 
                            { "name": "user", "type": "User" }
                        ]
                    }"""
                ),
                Arguments.of(
                    JsonSchema.TYPE,
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
    
                        "definitions": {
                            "User": {
                                "type": "object",
                                "properties": {
                                    "name": {"type": "string"}
                                },
                                "additionalProperties": false
                            }
                        }
                    }""",
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
    
                        "definitions": {
                            "Player": {
                                "type": "object",
                                "properties": {
                                    "identifier": {"type": "string"},
                                    "user": {"type": "User"}
                                },
                                "additionalProperties": false
                            }
                        }
                    }"""
                ),
                Arguments.of(
                    ProtobufSchema.TYPE,
                    """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    message User {
                        string name = 1;
                    }
                    """.trimIndent(),
                    """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    import "user.proto";
                    
                    message Player {
                        string identifier = 1;
                        User user = 2;
                    }
                    """.trimIndent()
                )
            )
    }
}
