package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories

@TestInstance(TestInstance.Lifecycle.PER_METHOD) // Separate TempDir per test
class ProtobufSchemaParserTest {

    private val schemaRegistryClient = MockSchemaRegistryClient()

    @TempDir
    lateinit var folderRule: Path

    @Test
    fun `Should keep the original file intact when not provided with references`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        givenALocalReference( // Creates the file that then gets ignored
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
              test.Address relativeAddress = 3;
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
                |  test.Address relativeAddress = 3;
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
              Address relativeAddress = 3;
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
                |  Address relativeAddress = 3;
                |}
    
                |message Address {
                |  string street = 1;
                |}
                |
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - resolving relative packages`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test.pkg.address;
            
            message Address {
              string street = 1;
            }
        """
        )
        val aUserSchemaFile = givenASchemaFile(
            "User.proto", """
                
            syntax = "proto3";
            
            package test.pkg;
            
            import "Address.proto";
            
            message User {
              string name = 1;
              .test.pkg.address.Address fullyQualifiedAddress = 2;
              test.pkg.address.Address fullAddress = 3;
              pkg.address.Address pkgAddress = 4;
              address.Address relativeAddress = 5;
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
    
                |package test.pkg;
    
                |message User {
                |  string name = 1;
                |  Address fullyQualifiedAddress = 2;
                |  Address fullAddress = 3;
                |  Address pkgAddress = 4;
                |  Address relativeAddress = 5;
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
              repeated Address relativeAddress = 3;
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
                |  repeated Address relativeAddress = 3;
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
              optional Address relativeAddress = 3;
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
                |  optional Address relativeAddress = 3;
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
              map<string, Address> relativeAddress = 3;
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
                |  map<string, Address> relativeAddress = 3;
                |}
    
                |message Address {
                |  string street = 1;
                |}
                |
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - transitive dependencies`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Street.proto", "Street.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Street {
              string name = 1;
            }
        """
        )
        val aTransitiveLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "Street.proto";
            
            message Address {
              .test.Street street = 1;
              Street relativeStreet = 2;
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
              .test.Address address = 2;
              Address relativeAddress = 3;
            }
        """
        )

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "User.proto",
            aUserSchemaFile.path,
            listOf(aLocalReference, aTransitiveLocalReference)
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
                |  Address relativeAddress = 3;
                |}
                |
                |message Address {
                |  Street street = 1;
                |  Street relativeStreet = 2;
                |}
                |
                |message Street {
                |  string name = 1;
                |}
                |
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - multiple target types in the same file`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Address {
              string street = 1;
            }
            
            enum AddressType {
              Local = 0;
              Overseas = 1;
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
              .test.Address address = 2;
              .test.AddressType addressType = 3;
              AddressType relativeAddressType = 4;
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
                |
                |syntax = "proto3";
                |
                |package test;
                |
                |message User {
                |  string name = 1;
                |  Address address = 2;
                |  AddressType addressType = 3;
                |  AddressType relativeAddressType = 4;
                |}
                |
                |message Address {
                |  string street = 1;
                |}
                |
                |enum AddressType {
                |  Local = 0;
                |  Overseas = 1;
                |}
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - different packages`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test.address;
            
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
              .test.address.Address address = 2;
              address.Address relativeAddress = 3;
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
                |
                |syntax = "proto3";
                |
                |package test;
                |
                |message User {
                |  string name = 1;
                |  Address address = 2;
                |  Address relativeAddress = 3;
                |}
                |
                |message Address {
                |  string street = 1;
                |}
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - transitive imports, different folders`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "address/Address.proto", "address/Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "street/Street.proto";
            
            message Address {
              Street street = 1;
            }
        """
        )
        val aTransitiveLocalReference = givenALocalReference(
            "street/Street.proto", "street/Street.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Street {
              string name = 1;
            }
        """
        )
        // A map can only be keyed by a scalar, so we only need to test the value.
        val aUserSchemaFile = givenASchemaFile(
            "User.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "address/Address.proto";
            
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
            listOf(aLocalReference, aTransitiveLocalReference)
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
                |message User {
                |  string name = 1;
                |  Address address = 2;
                |}
                |
                |message Address {
                |  Street street = 1;
                |}
                |
                |message Street {
                |  string name = 1;
                |}
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - target schema not in import root`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "address/Address.proto", "address/Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Address {
              string street = 1;
            }
        """
        )
        // A map can only be keyed by a scalar, so we only need to test the value.
        val aUserSchemaFile = givenASchemaFile(
            "users/User.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "address/Address.proto";
            
            message User {
              string name = 1;
              .test.Address address = 2;
            }
        """
        )

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "users/User.proto",
            aUserSchemaFile.path,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldLookLike(
            resolvedSchema,
            """
                |// Proto schema formatted by Wire, do not edit.
                |// Source: users/User.proto
                |
                |syntax = "proto3";
                |
                |package test;
                |
                |message User {
                |  string name = 1;
                |  Address address = 2;
                |}
                |
                |message Address {
                |  string street = 1;
                |}
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - nested target type`() {
        // Given
        val parser = ProtobufSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference(
            "Address.proto", "Address.proto", """
                
            syntax = "proto3";
            
            package test;
            
            message Address {
              string street = 1;
              
              enum AddressType {
                Local = 0;
                Overseas = 1;
              }
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
              .test.Address address = 2;
              .test.Address.AddressType addressType = 3;
              Address.AddressType relativeAddressType = 4;
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
                |
                |syntax = "proto3";
                |
                |package test;
                |
                |message User {
                |  string name = 1;
                |  Address address = 2;
                |  Address.AddressType addressType = 3;
                |  Address.AddressType relativeAddressType = 4;
                |}
                |
                |message Address {
                |  string street = 1;
                |  
                |  enum AddressType {
                |    Local = 0;
                |    Overseas = 1;
                |  }
                |}
                """.trimMargin()
        )
    }

    @Test
    fun `Should format local reference correctly - oneOf`() {
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
        val anotherLocalReference = givenALocalReference(
            "email/Email.proto", "email/Email.proto", """
                
            syntax = "proto3";
            
            package test.email;
            
            message Email {
              string address = 1;
            }
        """
        )
        // A map can only be keyed by a scalar, so we only need to test the value.
        val aUserSchemaFile = givenASchemaFile(
            "User.proto", """
                
            syntax = "proto3";
            
            package test;
            
            import "Address.proto";
            import "email/Email.proto";
            
            message User {
              string name = 1;
              oneof addressOrEmail {
                .test.Address address = 2;
                .test.email.Email email = 3;
              }
              oneof relativeAddressOrEmail {
                Address relativeAddress = 4;
                email.Email relativeEmail = 5;
              }
            }
        """
        )

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "User.proto",
            aUserSchemaFile.path,
            listOf(aLocalReference, anotherLocalReference)
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
                |message User {
                |  string name = 1;
                |  oneof addressOrEmail {
                |    Address address = 2;
                |    Email email = 3;
                |  }
                |  oneof relativeAddressOrEmail {
                |    Address relativeAddress = 4;
                |    Email relativeEmail = 5;
                |  }
                |}
                |
                |message Address {
                |  string street = 1;
                |}
                |
                |message Email {
                |  string address = 1;
                |}
                """.trimMargin()
        )
    }

    private fun givenALocalReference(referenceName: String, fileName: String, fileContent: String): LocalReference {
        val localReferenceFile = folderRule.resolve(fileName).toFile()
        localReferenceFile.toPath().parent.createDirectories()
        localReferenceFile.writeText(fileContent)
        return LocalReference(referenceName, localReferenceFile.path)
    }

    private fun givenASchemaFile(fileName: String, schemaContent: String): File {
        val schemaFile = folderRule.resolve(fileName).toFile()
        schemaFile.toPath().parent.createDirectories()
        schemaFile.writeText(schemaContent)
        return schemaFile
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
