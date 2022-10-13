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
            .withGradleVersion("6.7.1")
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
                |$userSubject, ${userFile.absolutePath}, \d
                |$playerSubject, $playerPath, \d
                |""".trimMargin())
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
        val subjectName = "parameterized-${type.name}"
        val extension = type.extension

        val userPath = "$type/user.$extension"
        val userFile = folderRule.newFile(userPath)
        userFile.writeText(userSchema)

        val playerPath = "$type/player.$extension"
        val playerSubject = "$subjectName-player"
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
}
