package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_METHOD) // Separate TempDir per test
class ProtobufSchemaParserTest {

    private val schemaRegistryClient = MockSchemaRegistryClient()

    @TempDir
    lateinit var folderRule: Path

    @Test
    @Disabled // It doesn't work at the moment
    fun `Should keep the original file intact when not provided with references`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val anIgnoredLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Address {
                string street = 1;
            }
        """
        )
        val aUserSchemaFile = givenASchemaFile(
            "User.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "Address.proto";
            
            message User {
              string name = 1;
              .test.Address address = 2;
            }
        """
        )

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "User.proto",
            aUserSchemaFile.path,
            emptyList()
        )

        // Then
        localSchemaShouldLookLike(
            resolvedSchema,
            """
                |// Proto schema formatted by Wire, do not edit.
                |// Source: User.proto
                |
                |syntax = "proto3";
                |
                |package test;
                |
                |import "Address.proto";
                |
                |message User {
                |  string name = 1;
                |  .test.Address address = 2;
                |}
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - message to message`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Address {
                string street = 1;
            }
        """
        )
        val aUserSchemaFile = givenASchemaFile(
            "User.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "Address.proto";
            
            message User {
              string name = 1;
              .test.Address address = 2;
            }
        """
        )

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "User.proto",
            aUserSchemaFile.path,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldLookLike(
            resolvedSchema,
            """
                |// Proto schema formatted by Wire, do not edit.
                |// Source: User.proto
    
                |syntax = "proto3";
    
                |package test;
    
                |message User {
                |  string name = 1;
                |  Address address = 2;
                |}
    
                |message Address {
                |  string street = 1;
                |}
                |
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - array to message`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Address {
                string street = 1;
            }
        """
        )
        // A map can only be keyed by a scalar, so we only need to test the value.
        val aUserSchemaFile = givenASchemaFile(
            "User.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "Address.proto";
            
            message User {
              string name = 1;
              repeated .test.Address address = 2;
            }
        """
        )

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "User.proto",
            aUserSchemaFile.path,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldLookLike(
            resolvedSchema,
            """
                |// Proto schema formatted by Wire, do not edit.
                |// Source: User.proto
    
                |syntax = "proto3";
    
                |package test;
    
                |message User {
                |  string name = 1;
                |  repeated Address address = 2;
                |}
    
                |message Address {
                |  string street = 1;
                |}
                |
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - optional to message`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Address {
                string street = 1;
            }
        """
        )
        // A map can only be keyed by a scalar, so we only need to test the value.
        val aUserSchemaFile = givenASchemaFile(
            "User.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "Address.proto";
            
            message User {
              string name = 1;
              optional .test.Address address = 2;
            }
        """
        )

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "User.proto",
            aUserSchemaFile.path,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldLookLike(
            resolvedSchema,
            """
                |// Proto schema formatted by Wire, do not edit.
                |// Source: User.proto
    
                |syntax = "proto3";
    
                |package test;
    
                |message User {
                |  string name = 1;
                |  optional Address address = 2;
                |}
    
                |message Address {
                |  string street = 1;
                |}
                |
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - map to message`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Address {
                string street = 1;
            }
        """
        )
        // A map can only be keyed by a scalar, so we only need to test the value.
        val aUserSchemaFile = givenASchemaFile(
            "User.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "Address.proto";
            
            message User {
              string name = 1;
              map<string, .test.Address> address = 2;
            }
        """
        )

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "User.proto",
            aUserSchemaFile.path,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldLookLike(
            resolvedSchema,
            """
                |// Proto schema formatted by Wire, do not edit.
                |// Source: User.proto
    
                |syntax = "proto3";
    
                |package test;
    
                |message User {
                |  string name = 1;
                |  map<string, Address> address = 2;
                |}
    
                |message Address {
                |  string street = 1;
                |}
                |
                """.trimMargin()
        )
    }

    private fun givenALocalReference(referenceName: String, fileName: String, fileContent: String): LocalReference {
        val addressLocalFile = folderRule.resolve(fileName).toFile()
        addressLocalFile.writeText(fileContent)
        return LocalReference(referenceName, addressLocalFile.path)
    }

    private fun givenASchemaFile(fileName: String, schemaContent: String): File {
        val userLocalFile = folderRule.resolve(fileName).toFile()
        userLocalFile.writeText(schemaContent)
        return userLocalFile
    }

    private fun localSchemaShouldLookLike(resolvedSchema: String, expectedSchema: String) {
        Assertions
            .assertThat(normalise(resolvedSchema))
            .isEqualTo(
                normalise(
                    expectedSchema
                )
            )
    }

    private fun normalise(proto: String): String {
        return proto.trim().replace("\\n\\s*\\n".toRegex(), "\n")
    }
}
