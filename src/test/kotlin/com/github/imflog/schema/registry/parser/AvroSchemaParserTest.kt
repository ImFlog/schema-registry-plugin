package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvroSchemaParserTest {

    private val schemaRegistryClient = MockSchemaRegistryClient()
    private val testFilesPath = "${Paths.get("").toAbsolutePath()}/src/test/resources/"

    @TempDir
    lateinit var folderRule: Path

    companion object {
        private const val ADDRESS_REFERENCE_NAME = "Address"
        private const val USER_REFERENCE_NAME = "User"
        private const val ADDRESS_SCHEMA = """{
             "type": "record",
             "name": "$ADDRESS_REFERENCE_NAME",
             "fields": [
                {"name": "street", "type": "string" }
             ]
        }"""
        private const val USER_SCHEMA = """{
             "type": "record",
             "name": "$USER_REFERENCE_NAME",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": "$ADDRESS_REFERENCE_NAME"}
             ]
        }"""

    }

    @Test
    fun `Should format local references correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, folderRule.toFile())
        val aLocalReference = givenALocalReference()
        val aUserSchemaFile = givenASchemaFile()

        // When
        val resolvedSchema = parser.resolveLocalReferences(
            USER_REFERENCE_NAME,
            aUserSchemaFile.path,
            listOf(aLocalReference)
        )

        // Then
        localSchemaShouldBeAppended(resolvedSchema)
    }

    private fun givenALocalReference(): LocalReference {
        val addressLocalFile = folderRule.resolve("Address.avsc").toFile()
        addressLocalFile.writeText(ADDRESS_SCHEMA)
        return LocalReference(ADDRESS_REFERENCE_NAME, addressLocalFile.path)
    }

    private fun givenASchemaFile(): File {
        val userLocalFile = folderRule.resolve("User.avsc").toFile()
        userLocalFile.writeText(USER_SCHEMA)
        return userLocalFile
    }

    private fun localSchemaShouldBeAppended(resolvedSchema: String) {
        Assertions
            .assertThat(
                JSONObject(resolvedSchema)
                    .getJSONArray("fields")
                    .getJSONObject(1)
                    .getJSONObject("type")
            )
            .usingRecursiveComparison() // To check the content of the JSON Objects instead of the equal method
            .isEqualTo(JSONObject(ADDRESS_SCHEMA))
    }

    @Test
    fun `Should resolve duplicated references with same namespace correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val reference = LocalReference("B", "${testFilesPath}testType.avsc")
        val schema = File("${testFilesPath}testSubject.avsc")
        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "test",
            schema.path,
            listOf(reference)
        )
        // Then
        val resolved = JSONObject(resolvedSchema).toString()

        @Language("JSON")
        val expected = """{
          "name": "A",
          "namespace": "com.mycompany",
          "type": "record",
          "fields": [
            {
              "name": "nested",
              "type": {
                "name":"B",
                "type": "enum",
                "symbols": ["X1", "X2"]
              }
            },
            {
              "name": "nested1",
              "type": "B"
            }
          ]
        }"""

        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }

    @Test
    fun `Should resolve complex nested array example correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val schema = File("${testFilesPath}ParentArraySubject.avsc")

        assertDoesNotThrow {
            parser.resolveLocalReferences(
                "test",
                schema.path,
                listOf(
                    LocalReference("NestedArrayType", "${testFilesPath}NestedArrayType.avsc"),
                    LocalReference("NestedNestedType", "${testFilesPath}NestedNestedType.avsc"),
                )
            )
        }
    }

    @Test
    fun `Should resolve complex nested record example correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val schema = File("${testFilesPath}ParentSubject.avsc")

        assertDoesNotThrow {
            parser.resolveLocalReferences(
                "test",
                schema.path,
                listOf(
                    LocalReference("NestedType", "${testFilesPath}NestedType.avsc"),
                    LocalReference("NestedNestedType", "${testFilesPath}NestedNestedType.avsc"),
                )
            )
        }
    }

    @Test
    fun `Should resolve duplicated array references correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val reference = LocalReference("B", "${testFilesPath}testType.avsc")
        val schema = File("${testFilesPath}testSubjectWithArrayReference.avsc")
        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "test",
            schema.path,
            listOf(reference)
        )
        // Then
        val resolved = JSONObject(resolvedSchema).toString()

        @Language("JSON")
        val expected = """{
           "name": "A",
           "namespace": "com.mycompany",
           "type": "record",
           "fields": [
             {
               "name": "nested",
               "type": {
                "type": "array",
                "items": {
                        "name":"B",
                        "type":"enum",
                        "symbols": ["X1","X2"]
                    }
                }
            },
            {
                "name":"nested1",
                "type":{
                    "type":"array",
                    "items":"com.mycompany.B"
                }
            }
          ]
        }"""

        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }

    @Test
    fun `Should parse with unknown remote references correctly`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val schema = File("${testFilesPath}testSubject.avsc")
        // When
        val resolvedSchema = parser.resolveLocalReferences(
            "test",
            schema.path,
            listOf()
        )
        // Then
        val resolved = JSONObject(resolvedSchema).toString()

        @Language("JSON")
        val expected = """{
          "type": "record",
          "name": "A",
          "namespace": "com.mycompany",
          "fields": [
            {
              "name": "nested",
              "type": "B"
            },
            {
              "name": "nested1",
              "type": "B"
            }
          ]
        }"""

        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }

    @Test
    fun `Should fix #199`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val mainRecord = File("${testFilesPath}/bug_199/main.avsc")

        // When
        val resolved = parser.resolveLocalReferences(
            "test",
            mainRecord.path,
            listOf(
                LocalReference("TypeA", "${testFilesPath}/bug_199/a.avsc"),
                LocalReference("TypeB", "${testFilesPath}/bug_199/b.avsc"),
                LocalReference("Shared", "${testFilesPath}/bug_199/shared.avsc")
            )
        )

        // Then
        val expected = """
        {
          "type": "record",
          "name": "MainRecord",
          "namespace": "com.example",
          "fields": [
            {
              "name": "example",
              "type": [
                {
                  "name": "TypeA",
                  "type": "record",
                  "fields": [
                    {
                       "name": "fooA",
                       "type": "string"
                    },
                    {
                      "name": "shared",
                      "type": [
                        "null",
                        {
                          "name": "Shared",
                          "type": "record",
                          "namespace": "com.example.common",
                          "fields": [
                            {
                              "name": "foobar",
                              "type": "string"
                            },
                            {
                              "name": "bar",
                              "type": "string"
                            }
                          ]
                        }
                      ]
                    }
                   
                  ]
                },
                {
                  "name": "TypeB",
                  "type": "record",
                  "fields": [
                    {
                       "name": "fooB",
                       "type": "string"
                    },
                    {
                      "name": "shared",
                      "type": ["null", "com.example.common.Shared"]
                    }
                   
                  ]
                }
              ]
            }
          ]
        }    
        """
        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }

    @Test
    fun `Should fix #204`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val mainRecord = File("${testFilesPath}/bug_204/mainMap.avsc")

        // When
        val resolved = parser.resolveLocalReferences(
            "test",
            mainRecord.path,
            listOf(
                LocalReference("Map", "${testFilesPath}/bug_204/map.avsc")
            )
        )

        // Then
        val expected = """
        {
          "type": "record",
          "name": "MainMap",
          "namespace": "com.example",
          "fields": [
            {
              "name": "foo",
              "type": "string"
            },
            {
               "default": {},
               "name": "types",
               "type": {
                 "type": "map",
                 "values": {
                     "name": "Map",
                     "type": "record",
                     "fields": [
                        {
                          "name": "foo",
                          "type": ["null", "string"],
                          "default": null
                        },
                        {
                          "name": "bar",
                          "type": ["null", 
                              {
                                 "type": "array",
                                 "items": "string"
                              }
                          ],
                          "default": null
                        },
                        {
                           "name": "foobar",
                           "type": [
                              "null",
                              {
                                  "type": "map",
                                  "values": "string"
                              }
                           ],
                           "default": null
                        }
                     ]
                   }
                 }
            }
          ]
        }    
        """
        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }

    @Test
    fun `Should fix #206`() {
        // Given
        val parser = AvroSchemaParser(schemaRegistryClient, File(testFilesPath))
        val mainRecord = File("${testFilesPath}/bug_206/Main.avsc")

        // When
        val resolved = parser.resolveLocalReferences(
            "test",
            mainRecord.path,
            listOf(
                LocalReference("Common", "${testFilesPath}/bug_206/Common.avsc"),
                LocalReference("NestedOne", "${testFilesPath}/bug_206/NestedOne.avsc"),
                LocalReference("NestedTwo", "${testFilesPath}/bug_206/NestedTwo.avsc")

            )
        )

        val expected = """
        {
            "name": "MainRecord",
            "namespace": "com.example",
            "type": "record",
            "fields":
            [
                {
                    "name": "foo",
                    "type":
                    {
                        "name": "NestedOne",
                        "namespace": "com.example.one",
                        "type": "record",
                        "fields":
                        [
                            {
                                "name": "foo",
                                "type": "string"
                            },
                            {
                                "name": "bar",
                                "type":
                                {
                                    "name": "Common",
                                    "namespace": "com.common.example",
                                    "type": "record",
                                    "fields":
                                    [
                                        {
                                            "name": "foo",
                                            "type": "string"
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                },
                {
                    "name": "bar",
                    "type":
                    {
                        "name": "NestedTwo",
                        "namespace": "com.common.example",
                        "type": "record",
                        "fields":
                        [
                            {
                                "name": "foo",
                                "type": "string"
                            },
                            {
                                "name": "bar",
                                "type": "com.common.example.Common"
                            }
                        ]
                    }
                }
            ]
        }    
        """
        Assertions.assertThat(resolved).isEqualTo(
            JSONObject(expected).toString()
        )
    }
}
