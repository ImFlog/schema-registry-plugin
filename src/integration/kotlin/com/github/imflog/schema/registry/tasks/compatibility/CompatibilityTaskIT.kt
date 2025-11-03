package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.Subject
import com.github.imflog.schema.registry.parser.SchemaParser
import com.github.imflog.schema.registry.utils.KafkaTestContainersUtils
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
import org.junit.jupiter.params.support.ParameterDeclarations
import java.io.File
import java.util.stream.Stream

class CompatibilityTaskIT : KafkaTestContainersUtils() {
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
        type: SchemaType,
        userSchema: ParsedSchema,
        playerSchema: ParsedSchema,
        playerSchemaUpdated: String
    ) {
        folderRule.create()
        folderRule.newFolder(type.name)

        val subjectName = "parameterized-${type.name}"

        val userSubject = "$subjectName-user"
        client.register(userSubject, userSchema)

        val extension = type.extension
        val playerPath = "$type/player.$extension"
        val playerSubject = "$subjectName-player"

        client.register(playerSubject, playerSchema)

        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchemaUpdated)

        // Small trick, for protobuf the name to import is not User but user.proto
        val referenceName = if (type == SchemaType.PROTOBUF) "user.proto" else "User"

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
                    subject('$playerSubject', '${playerFile.absolutePath}', "${type.name}").addReference('$referenceName', '$userSubject', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
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
        type: SchemaType,
        userSchema: ParsedSchema,
        playerSchema: ParsedSchema,
        playerSchemaUpdated: String
    ) {
        folderRule.create()
        folderRule.newFolder(type.name)

        val subjectName = "parameterized-${type.name}"

        val userSubject = "$subjectName-user"
        client.register(userSubject, userSchema)

        val playerPath = "$type/player.${type.extension}"
        val playerSubject = "$subjectName-player"

        client.register(playerSubject, playerSchema)

        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchemaUpdated)

        // Small trick, for protobuf the name to import is not User but user.proto
        val referenceName = if (type == SchemaType.PROTOBUF) "user.proto" else "User"

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
                    subject('$playerSubject', '$playerPath', "${type.name}").addReference('$referenceName', '$userSubject', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @ParameterizedTest
    @ArgumentsSource(SchemaLocalReferenceSuccessArgumentProvider::class)
    fun `CompatibilityTask should succeed for compatible schemas with local references`(
        type: SchemaType,
        userSchema: ParsedSchema,
        playerSchema: ParsedSchema,
        playerSchemaUpdated: String
    ) {
        folderRule.create()
        folderRule.newFolder(type.name)

        val subjectName = "parameterized-$type"
        val playerPath = "$type/player.${type.extension}"
        val playerSubject = "$subjectName-player"

        client.register(playerSubject, playerSchema)

        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchemaUpdated)

        val userPath = "$type/user.${type.extension}"
        val userFile = folderRule.newFile(userPath)
        userFile.writeText(userSchema.canonicalString())

        // Small trick, for protobuf the name to import is not User but user.proto
        val referenceName = if (type == SchemaType.PROTOBUF) "user.proto" else "User"

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
            .withGradleVersion("8.6")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @ParameterizedTest
    @ArgumentsSource(SchemaLocalAndRemoteReferenceSuccessArgumentProvider::class)
    fun `CompatibilityTask should succeed for compatible schemas with mixed references`(
        type: SchemaType,
        addressSchema: String,
        userSchema: String,
        playerSchema: String,
        playerSchemaUpdated: String
    ) {
        // TODO: Instead of repeating code, we could create build.gradle files in resources.
        //  Also, when all format support mixed local + remote, we will keep only this test.
        folderRule.create()
        val rootFolder = folderRule.newFolder(type.name)
        val parser = SchemaParser.provide(type, client, rootFolder)
        val subjectName = "parameterized-mixed"
        val extension = type.extension

        // Local
        val userPath = "$type/user.$extension"
        val userFile = folderRule.newFile(userPath)
        userFile.writeText(userSchema)
        val userSubject = "User"

        // Remote
        val addressPath = "$type/address.$extension"
        val addressFile = folderRule.newFile(addressPath)
        addressFile.writeText(addressSchema)
        val addressReferenceName = "Address"
        val addressSubject = Subject("$subjectName-address-mixed",addressFile.path, type.toString())
        client.register(addressSubject.inputSubject, parser.parseSchemaFromFile(addressSubject))

        val playerPath = "$type/player.$extension"
        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchema)
        val playerSubject = Subject("$subjectName-player-mixed",playerFile.path, type.toString())
        client.register(
            playerSubject.inputSubject,
            parser.parseSchemaFromFile(
                playerSubject
            )
        )

        // Update player schema file in place
        playerFile.writeText(playerSchemaUpdated)

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
                    subject('${playerSubject.inputSubject}', '${playerFile.absolutePath}', '${type.name}')
                        .addLocalReference('$userSubject', '$userPath')
                        .addReference('$addressReferenceName', '${addressSubject.inputSubject}', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
            .withProjectDir(folderRule.root)
            .withArguments(CompatibilityTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":testSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    private class SchemaSuccessArgumentProvider : ArgumentsProvider {
        override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> =
            Stream.of(
                Arguments.of(
                    SchemaType.AVRO,
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
                    SchemaType.JSON,
                    JsonSchema(
                        $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "User",
        
                            "type": "object",
                            "properties": {
                                "name": { "type": "string" }
                            },
                            "additionalProperties": false
                        }"""
                    ),
                    JsonSchema(
                        $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "Player",
                            "type": "object",
                            "properties": {
                                "identifier": { "type": "string" }
                            },
                            "additionalProperties": false
                        }"""
                    ),
                    $$"""{
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "$id": "Player",
    
                        "type": "object",
                        "properties": {
                            "identifier": { "type": "string" },
                            "user": { "$ref": "#User" }
                        },
                        "additionalProperties": false
                    }"""
                ),
                Arguments.of(
                    SchemaType.PROTOBUF,
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

    private class SchemaLocalAndRemoteReferenceSuccessArgumentProvider : ArgumentsProvider {
        override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> =
            Stream.of(
                Arguments.of(
                    SchemaType.AVRO,
                    """{
                        "type": "record",
                        "name": "Address",
                        "fields": [
                            { "name": "street", "type": "string" }
                        ]
                    }""",
                    """{
                        "type": "record",
                        "name": "User",
                        "fields": [
                            { "name": "name", "type": "string" },
                            { "name": "address", "type": "Address" }
                        ]
                    }""",
                    """{
                        "type": "record",
                        "name": "Player",
                        "fields": [
                            { "name": "identifier", "type": "string" }
                        ]
                    }""",
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
                    SchemaType.JSON,
                    $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "Address",
                            "type": "object",
                            "properties": {
                                "street": {"type": "string"}
                            },
                            "additionalProperties": false
                        }""",
                    $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "User",
                            "type": "object",
                            "properties": {
                                "name": {"type": "string"},
                                "address": {"$ref": "Address"}
                            },
                            "additionalProperties": false
                        }""",

                    $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "Player",
                            "type": "object",
                            "properties": {
                                "identifier": { "type": "string" }
                            },
                            "additionalProperties": false
                        }""",
                    $$"""{
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "$id": "Player",
                        "type": "object",
                        "properties": {
                            "identifier": { "type": "string" },
                            "user": { "$ref": "#User" }
                        },
                        "additionalProperties": false
                    }"""
                ),
                // TODO: Uncomment this when the other types support local references
//                Arguments.of(
//                    SchemaType.PROTOBUF,
//                        """
//                    syntax = "proto3";
//                    package com.github.imflog;
//
//                    message Address {
//                        string street = 1;
//                    }
//                    """,
//                    """
//                    syntax = "proto3";
//                    package com.github.imflog;
//
//                    import "address.proto";

//                    message User {
//                        string name = 1;
//                        Address address = 2;
//                    }
//                    """,
//                    """
//                    syntax = "proto3";
//                    package com.github.imflog;
//
//                    message Player {
//                        string identifier = 1;
//                    }
//                    """ ,
//                    """
//                    syntax = "proto3";
//                    package com.github.imflog;
//
//                    import "user.proto";
//
//                    message Player {
//                        string identifier = 1;
//                        User user = 2;
//                    }
//                    """
            )
    }

    private class SchemaLocalReferenceSuccessArgumentProvider : ArgumentsProvider {
        override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> =
            Stream.of(
                Arguments.of(
                    SchemaType.AVRO,
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
                    SchemaType.JSON,
                    JsonSchema(
                        $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "User",
                            "type": "object",
                            "properties": {
                                "name": { "type": "string" }
                            },
                            "additionalProperties": false
                        }"""
                    ),
                    JsonSchema(
                        $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "Player",
                            "type": "object",
                            "properties": {
                                "identifier": { "type": "string" }
                            },
                            "additionalProperties": false
                        }"""
                    ),
                    $$"""{
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "$id": "Player",
                        "type": "object",
                        "properties": {
                            "identifier": { "type": "string" },
                            "user": { "$ref": "#User" }
                        },
                        "additionalProperties": false
                    }"""
                ),
                // TODO: Uncomment this when the other types support local references
//                Arguments.of(
//                    SchemaType.PROTOBUF,
//                    ProtobufSchema(
//                        """
//                    syntax = "proto3";
//                    package com.github.imflog;
//
//                    message User {
//                        string name = 1;
//                    }
//                    """
//                    ),
//                    ProtobufSchema(
//                        """
//                    syntax = "proto3";
//                    package com.github.imflog;
//
//                    message Player {
//                        string identifier = 1;
//                    }
//                    """
//                    ),
//                    """
//                    syntax = "proto3";
//                    package com.github.imflog;
//
//                    import "user.proto";
//
//                    message Player {
//                        string identifier = 1;
//                        User user = 2;
//                    }
//                    """
//                )
            )
    }

    private class SchemaFailureArgumentProvider : ArgumentsProvider {
        override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> =
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
                        $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "User",
        
                            "type": "object",
                            "properties": {
                                "name": { "type": "string" }
                            },
                            "additionalProperties": false
                        }"""
                    ),
                    JsonSchema(
                        $$"""{
                            "$schema": "http://json-schema.org/draft-07/schema#",
                            "$id": "Player",
        
                            "type": "object",
                            "properties": {
                                "identifier": { "type": "string" },
                                "user": { "$ref": "#User" }
                            },
                            "additionalProperties": false
                        }"""
                    ),
                    $$"""{
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "$id": "Player",
    
                        "properties": {
                            "identifier": {"type": "number"},
                            "user": { "$ref": "#User" }
                        },
                        "additionalProperties": false
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
