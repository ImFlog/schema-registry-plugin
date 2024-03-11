package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.SchemaType
import com.github.imflog.schema.registry.utils.KafkaTestContainersUtils
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

class RegisterTaskIT : KafkaTestContainersUtils() {
    private lateinit var folderRule: TemporaryFolder
    private lateinit var buildFile: File

    @BeforeEach
    fun beforeEach() {
        folderRule = TemporaryFolder()
    }

    @AfterEach
    fun afterEach() {
        client.reset()
        folderRule.delete()
    }

    @ParameterizedTest
    @ArgumentsSource(SchemaArgumentProvider::class)
    fun `RegisterSchemasTask should register schemas`(
        type: SchemaType,
        userSchema: String,
        playerSchema: String
    ) {
        folderRule.create()
        val typeFolder = folderRule.newFolder(type.name)
        val resultFolder = folderRule.newFolder("${type.name}/results")
        val subjectName = "parameterized-${type.name}"
        val extension = type.extension

        val userFile = typeFolder.resolve("user.$extension")
        userFile.writeText(userSchema)
        val userSubject = "$subjectName-user-local"

        val playerFile = typeFolder.resolve("player.$extension")
        val playerPath = playerFile.relativeTo(folderRule.root).path
        playerFile.writeText(playerSchema)
        val playerSubject = "$subjectName-player-local"

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
                outputDirectory = '${resultFolder.absolutePath}'
                register {
                    subject('$userSubject', '${userFile.absolutePath}', '${type.name}')
                    subject('$playerSubject', '$playerPath', '${type.name}')
                        .addReference('$referenceName', '$userSubject', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
            .withProjectDir(folderRule.root)
            .withArguments(RegisterSchemasTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        Assertions.assertThat(resultFolder.resolve("registered.csv")).exists()
        Assertions.assertThat(resultFolder.resolve("registered.csv").readText())
            .matches(
                """subject, path, id
                |$userSubject, ${userFile.absolutePath}, \d+
                |$playerSubject, $playerPath, \d+
                |""".trimMargin()
            )
    }

    @ParameterizedTest
    @ArgumentsSource(SchemaWithMetadataAndRuleSetArgumentProvider::class)
    fun `RegisterSchemasTask should register normalised schemas with metadata and rulesets`(
        type: SchemaType,
        userSchema: String,
        playerSchema: String,
        metadata: String,
        ruleSet: String,
    ) {
        folderRule.create()
        val typeFolder = folderRule.newFolder(type.name)
        val resultFolder = folderRule.newFolder("${type.name}/results")
        val subjectName = "parameterized-${type.name}"
        val extension = type.extension

        val userFile = typeFolder.resolve("user.$extension")
        userFile.writeText(userSchema)
        val userSubject = "$subjectName-user-local"

        val metadataFile = typeFolder.resolve("metadata.json")
        metadataFile.writeText(metadata)

        val ruleSetFile = typeFolder.resolve("ruleSet.json")
        ruleSetFile.writeText(ruleSet)

        val playerFile = typeFolder.resolve("player.$extension")
        val playerPath = playerFile.relativeTo(folderRule.root).path
        playerFile.writeText(playerSchema)
        val playerSubject = "$subjectName-player-local"

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
                outputDirectory = '${resultFolder.absolutePath}'
                register {
                    subject('$userSubject', '${userFile.absolutePath}', '${type.name}')
                        .setMetadata('${metadataFile.absolutePath}')
                        .setRuleSet('${ruleSetFile.absolutePath}')
                        .setNormalized(true)
                    subject('$playerSubject', '$playerPath', '${type.name}')
                        .addReference('$referenceName', '$userSubject', 1)
                        .setNormalized(true)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
            .withProjectDir(folderRule.root)
            .withArguments(RegisterSchemasTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        Assertions.assertThat(resultFolder.resolve("registered.csv")).exists()
        Assertions.assertThat(resultFolder.resolve("registered.csv").readText())
            .matches(
                """subject, path, id
                |$userSubject, ${userFile.absolutePath}, \d+
                |$playerSubject, $playerPath, \d+
                |""".trimMargin()
            )
    }

    @ParameterizedTest
    @ArgumentsSource(LocalSchemaArgumentProvider::class)
    fun `Should register local references`(
        type: SchemaType,
        userSchema: String,
        playerSchema: String
    ) {
        folderRule.create()
        folderRule.newFolder(type.name)
        val subjectName = "parameterized-${type.name}-local"
        val extension = type.extension

        val userPath = "$type/user.$extension"
        val userFile = folderRule.newFile(userPath)
        userFile.writeText(userSchema)

        val playerPath = "$type/player.$extension"
        val playerSubject = "$subjectName-player-local"
        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchema)

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
                register {
                    subject('$playerSubject', '$playerPath', '${type.name}')
                        .addLocalReference('$referenceName', '$userPath')
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
            .withProjectDir(folderRule.root)
            .withArguments(RegisterSchemasTask.TASK_NAME)
            .withPluginClasspath()
            .withDebug(true)
            .build()
        Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @ParameterizedTest
    @ArgumentsSource(RemoteLocalSchemaArgumentProvider::class)
    fun `Should register mixed references`(
        type: SchemaType,
        addressSchema: String,
        userSchema: String,
        playerSchema: String
    ) {
        // TODO: Instead of repeating code, we could create build.gradle files in resources.
        //  Also, when all format support mixed local + remote, we will keep only this test.
        folderRule.create()
        folderRule.newFolder(type.name)
        val subjectName = "parameterized-${type.name}-mixed"
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
        val addressSubject = "$subjectName-address-mixed"

        val playerPath = "$type/player.$extension"
        val playerSubject = "$subjectName-player-mixed"
        val playerFile = folderRule.newFile(playerPath)
        playerFile.writeText(playerSchema)

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
                    subject('$addressSubject', '${addressFile.absolutePath}', '${type.name}')
                    subject('$playerSubject', '$playerPath', '${type.name}')
                        .addLocalReference('$userSubject', '$userPath')
                        .addReference('$addressReferenceName', '$addressSubject', 1)
                }
            }
        """
        )

        val result: BuildResult? = GradleRunner.create()
            .withGradleVersion("8.6")
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
                    SchemaType.AVRO,
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
                    SchemaType.JSON,
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"}
                        },
                        "additionalProperties": false
                    }""",
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "identifier": {"type": "string"},
                            "user": {"${"$"}ref": "User"}
                        },
                        "additionalProperties": false
                    }"""
                ),
                Arguments.of(
                    SchemaType.PROTOBUF,
                    """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    message User {
                        string name = 1;
                    }
                    """,
                    """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    import "user.proto";
                    
                    message Player {
                        string identifier = 1;
                        User user = 2;
                    }
                    """,
                )
            )
    }

    private class SchemaWithMetadataAndRuleSetArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> =
            Stream.of(
                Arguments.of(
                    SchemaType.AVRO,
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
                            { "name": "identifier", "type": "string", "confluent:tags": [ "PII" ] }, 
                            { "name": "user", "type": "User" }
                        ]
                    }""",
                    """{
                      "domainRules": [
                        {
                          "name": "encryptPII",
                          "kind": "TRANSFORM",
                          "type": "ENCRYPT",
                          "mode": "WRITEREAD",
                          "tags": [
                            "PII"
                          ],
                          "params": {
                            "encrypt.kek.name": "kafka-csfle",
                            "encrypt.kms.key.id": "projects/gcp-project/locations/europe-west6/keyRings/gcp-keyring/cryptoKeys/kafka-csfle",
                            "encrypt.kms.type": "gcp-kms"
                          },
                          "onFailure": "ERROR,NONE"
                        }
                      ]
                    }""",
                    """{
                      "tags": {
                        "**.ssn": [ "PII" ]
                      },
                      "properties": {
                        "owner": "Bob Jones",
                        "email": "bob@acme.com"
                      }
                    }"""
                ),
                Arguments.of(
                    SchemaType.JSON,
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string",
                                "confluent:tags": [ "PII" ]
                            }
                        },
                        "additionalProperties": false
                    }""",
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "identifier": {"type": "string"},
                            "user": {"${"$"}ref": "User"}
                        },
                        "additionalProperties": false
                    }""",
                    """{
                      "domainRules": [
                        {
                          "name": "encryptPII",
                          "kind": "TRANSFORM",
                          "type": "ENCRYPT",
                          "mode": "WRITEREAD",
                          "tags": [
                            "PII"
                          ],
                          "params": {
                            "encrypt.kek.name": "kafka-csfle",
                            "encrypt.kms.key.id": "projects/gcp-project/locations/europe-west6/keyRings/gcp-keyring/cryptoKeys/kafka-csfle",
                            "encrypt.kms.type": "gcp-kms"
                          },
                          "onFailure": "ERROR,NONE"
                        }
                      ]
                    }""",
                    """{
                      "tags": {
                        "**.ssn": [ "PII" ]
                      },
                      "properties": {
                        "owner": "Bob Jones",
                        "email": "bob@acme.com"
                      }
                    }"""
                ),
                Arguments.of(
                    SchemaType.PROTOBUF,
                    """
                    syntax = "proto3";
                    import "confluent/meta.proto";

                    package com.github.imflog;
                    
                    option java_package = "com.github.proto.imflog.v1";
                    option java_multiple_files = true;
                    option java_outer_classname = "UserProto";
                    
                    message User {
                        string name = 1 [(.confluent.field_meta).tags = "PII"];
                    }
                    """,
                    """
                    syntax = "proto3";
                    package com.github.imflog;
                    
                    import "user.proto";
                    
                    message Player {
                        string identifier = 1;
                        User user = 2;
                    }
                    """,
                    """{
                      "domainRules": [
                        {
                          "name": "encryptPII",
                          "kind": "TRANSFORM",
                          "type": "ENCRYPT",
                          "mode": "WRITEREAD",
                          "tags": [
                            "PII"
                          ],
                          "params": {
                            "encrypt.kek.name": "kafka-csfle",
                            "encrypt.kms.key.id": "projects/gcp-project/locations/europe-west6/keyRings/gcp-keyring/cryptoKeys/kafka-csfle",
                            "encrypt.kms.type": "gcp-kms"
                          },
                          "onFailure": "ERROR,NONE"
                        }
                      ]
                    }""",
                    """{
                      "tags": {
                        "**.ssn": [ "PII" ]
                      },
                      "properties": {
                        "owner": "Bob Jones",
                        "email": "bob@acme.com"
                      }
                    }"""
                )
            )
    }

    private class LocalSchemaArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> =
            Stream.of(
                Arguments.of(
                    SchemaType.AVRO,
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
                    SchemaType.JSON,
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "User",
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"}
                        },
                        "additionalProperties": false
                    }""",
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "identifier": {"type": "string"},
                            "user": {"${"$"}ref": "User"}
                        },
                        "additionalProperties": false
                    }"""
                ),
                // TODO: Uncomment this when the other types support local references
//                Arguments.of(
//                    SchemaType.PROTOBUF,
//                    """
//                    syntax = "proto3";
//                    package com.github.imflog;
//
//                    message User {
//                        string name = 1;
//                    }
//                    """,
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
//                    """,
//                )
            )
    }

    private class RemoteLocalSchemaArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> =
            Stream.of(
                Arguments.of(
                    SchemaType.JSON,
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "Address",
                        "type": "object",
                        "properties": {
                            "street": {"type": "string"}
                        },
                        "additionalProperties": false
                    }""",
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "User",
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "address": {"${"$"}ref": "Address"}
                        },
                        "additionalProperties": false
                    }""",
                    """{
                        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
                        "${"$"}id": "Player",
                        "type": "object",
                        "properties": {
                            "identifier": {"type": "string"},
                            "user": {"${"$"}ref": "User"}
                        },
                        "additionalProperties": false
                    }"""
                ),
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
                            { "name": "identifier", "type": "string" }, 
                            { "name": "user", "type": "User" }
                        ]
                    }"""
                )
            )
    }
}
