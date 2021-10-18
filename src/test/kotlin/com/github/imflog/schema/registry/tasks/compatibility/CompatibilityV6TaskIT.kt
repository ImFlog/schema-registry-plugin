package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.utils.Kafka6TestContainersUtils
import io.confluent.kafka.schemaregistry.ParsedSchema
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

class CompatibilityV6TaskIT : Kafka6TestContainersUtils() {
    private lateinit var folderRule: TemporaryFolder
    private lateinit var buildFile: File

    @BeforeEach
    fun init() {
        folderRule = TemporaryFolder()
    }

    @AfterEach
    fun tearDown() {
        client.allSubjects.reversed().forEach {
            client.deleteSubject(it)
        }
        folderRule.delete()
    }

    @ParameterizedTest
    @ArgumentsSource(SchemaSuccessArgumentProvider::class)
    fun `CompatibilityTask should succeed for compatible schemas`(
        type: String,
        userSchema: ParsedSchema,
        playerSchema: ParsedSchema,
        playerSchemaUpdated: String
    ) {
        folderRule.create()
        folderRule.newFolder(type)

        val subjectName = "parameterized-$type"

        val userSubject = "$subjectName-user"
        client.register(userSubject, userSchema)

        val extension = when (type) {
            AvroSchema.TYPE -> "avsc"
            ProtobufSchema.TYPE -> "proto"
            JsonSchema.TYPE -> "json"
            else -> throw Exception("Should not happen")
        }
        val playerPath = "$type/player.$extension"
        val playerSubject = "$subjectName-player"

        client.register(playerSubject, playerSchema)

        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchemaUpdated)

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
                compatibility {
                    subject('$playerSubject', '${playerFile.absolutePath}', "$type").addReference('$referenceName', '$userSubject', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @ParameterizedTest
    @ArgumentsSource(SchemaFailureArgumentProvider::class)
    fun `CompatibilityTask should fail for incompatible schemas`(
        type: String,
        userSchema: ParsedSchema,
        playerSchema: ParsedSchema,
        playerSchemaUpdated: String
    ) {
        folderRule.create()
        folderRule.newFolder(type)

        val subjectName = "parameterized-$type"

        val userSubject = "$subjectName-user"
        client.register(userSubject, userSchema)

        val extension = when (type) {
            AvroSchema.TYPE -> "avsc"
            ProtobufSchema.TYPE -> "proto"
            JsonSchema.TYPE -> "json"
            else -> throw Exception("Should not happen")
        }
        val playerPath = "$type/player.$extension"
        val playerSubject = "$subjectName-player"

        client.register(playerSubject, playerSchema)

        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchemaUpdated)

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
                compatibility {
                    subject('$playerSubject', '$playerPath', "$type").addReference('$referenceName', '$userSubject', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @ParameterizedTest
    @ArgumentsSource(SchemaSuccessArgumentProvider::class)
    fun `CompatibilityTask should succeed for compatible schemas with local dependencies`(
        type: String,
        userSchema: ParsedSchema,
        playerSchema: ParsedSchema,
        playerSchemaUpdated: String
    ) {
        folderRule.create()
        folderRule.newFolder(type)

        val extension = when (type) {
            AvroSchema.TYPE -> "avsc"
            ProtobufSchema.TYPE -> "proto"
            JsonSchema.TYPE -> "json"
            else -> throw Exception("Should not happen")
        }
        val subjectName = "parameterized-$type"
        val playerPath = "$type/player.$extension"
        val playerSubject = "$subjectName-player"

        client.register(playerSubject, playerSchema)

        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchemaUpdated)

        val userPath = "$type/user.$extension"
        val userFile = folderRule.newFile(userPath)
        userFile.writeText(userSchema.canonicalString())

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
                compatibility {
                    subject('$playerSubject', '${playerFile.absolutePath}', "$type")
                        .addLocalReference('$referenceName', '$userPath')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("6.7.1")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    private class SchemaSuccessArgumentProvider : ArgumentsProvider {
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
                        "name": "Player",
                        "fields": [
                            { "name": "identifier", "type": "string" }
                        ]
                    }"""
                    ),
                    """{
                        "type": "record",
                        "name": "Player",
                        "fields": [
                            { "name": "identifier", "type": "string" },
                            { "name": "user", "type": ["null", "User"], "default": null}
                        ]
                    }"""
                ),
                Arguments.of(
                    JsonSchema.TYPE,
                    JsonSchema(
                        """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "http://github.com/imflog/kafka-schema-registry/user.json",
    
                        "definitions": {
                            "User": {
                                "type": "object",
                                "properties": {
                                    "name": { "type": "string" }
                                },
                                "additionalProperties": false
                            }
                        },
                        "properties": {
                            "user": { "${"$"}ref": "#/definitions/User" }
                        }
                    }"""
                    ),
                    JsonSchema(
                        """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "http://github.com/imflog/kafka-schema-registry/player.json",
    
                        "definitions": {
                            "Player": {
                                "type": "object",
                                "properties": {
                                    "identifier": { "type": "string" }
                                },
                                "additionalProperties": false
                            }
                        },
                        
                        "properties": {
                            "player": { "${"$"}ref": "#/definitions/Player" }
                        }
                    }"""
                    ),
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "http://github.com/imflog/kafka-schema-registry/player.json",
    
                        "definitions": {
                            "Player": {
                                "type": "object",
                                "properties": {
                                    "identifier": { "type": "string" },
                                    "user": { "${"$"}ref": "#user" }
                                },
                                "additionalProperties": false
                            }
                        },
                        "properties": {
                            "player": { "${"$"}ref": "#/definitions/Player" }
                        }
                    }"""
                ),
                Arguments.of(
                    ProtobufSchema.TYPE,
                    ProtobufSchema(
                        """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    message User {
                        string name = 1;
                    }
                    """
                    ),
                    ProtobufSchema(
                        """
                    syntax = "proto3";
                    package com.github.imflog;

                    message Player {
                        string identifier = 1;
                    }
                    """
                    ),
                    """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    import "user.proto";
                    
                    message Player {
                        string identifier = 1;
                        User user = 2;
                    }
                    """
                )
            )
    }

    private class SchemaFailureArgumentProvider : ArgumentsProvider {
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
                        "name": "Player",
                        "fields": [
                            { "name": "identifier", "type": "string" }
                        ]
                    }"""
                    ),
                    """{
                        "type": "record",
                        "name": "Player",
                        "fields": [
                            { "name": "identifier", "type": "string" },
                            { "name": "user", "type": "User"}
                        ]
                    }"""
                ),
                Arguments.of(
                    JsonSchema.TYPE,
                    JsonSchema(
                        """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "http://github.com/imflog/kafka-schema-registry/user.json",
    
                        "definitions": {
                            "User": {
                                "type": "object",
                                "properties": {
                                    "name": { "type": "string" }
                                },
                                "additionalProperties": false
                            }
                        },
                        "properties": {
                            "user": { "${"$"}ref": "#/definitions/User" }
                        }
                    }"""
                    ),
                    JsonSchema(
                        """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "http://github.com/imflog/kafka-schema-registry/player.json",
    
                        "definitions": {
                            "Player": {
                                "type": "object",
                                "properties": {
                                    "identifier": { "type": "string" }
                                },
                                "additionalProperties": false
                            }
                        },
                        
                        "properties": {
                            "player": { "${"$"}ref": "#/definitions/Player" }
                        }
                    }"""
                    ),
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "http://github.com/imflog/kafka-schema-registry/player.json",
    
                        "definitions": {
                            "Player": {
                                "type": "number"
                            }
                        },
                        "properties": {
                            "player": { "${"$"}ref": "#/definitions/Player" }
                        }
                    }"""
                ),
                Arguments.of(
                    ProtobufSchema.TYPE,
                    ProtobufSchema(
                        """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    message User {
                        string name = 1;
                    }
                    """
                    ),
                    ProtobufSchema(
                        """
                    syntax = "proto3";
                    package com.github.imflog;

                    message Player {
                        string identifier = 1;
                    }
                    """
                    ),
                    """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    import "user.proto";
                    
                    message Player {
                        User user = 1;
                        string identifier = 2;
                    }
                    """
                )
            )
    }
}
