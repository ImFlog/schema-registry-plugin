package com.github.imflog.schema.registry.tasks.compatibility

import com.github.imflog.schema.registry.Subject
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.rest.exceptions.Errors
import io.mockk.every
import io.mockk.spyk
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class CompatibilityTaskActionTest {

    lateinit var folderRule: TemporaryFolder

    @BeforeEach
    fun setUp() {
        folderRule = TemporaryFolder()
        folderRule.create()
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `Should verify compatibility with no references`() {
        // given
        val registryClient = MockSchemaRegistryClient()
        registryClient.register(
            "test",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )
        folderRule.newFolder("src", "main", "avro", "external")

        val subjects =
            arrayListOf(Subject("test", "src/main/avro/external/test.avsc", "AVRO"))
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "newField", "type": "string", "default": ""}
             ]
            }
        """
        )

        // when
        val errorCount = CompatibilityTaskAction(
            registryClient,
            folderRule.root,
            subjects
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should verify compatibility with references`() {
        // given
        val registryClient = MockSchemaRegistryClient()
        registryClient.register(
            "test",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        // Register dependency
        registryClient.register(
            "Street",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Street",
                    "fields": [
                        {"name": "street", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        registryClient.register(
            "Address",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Address",
                    "fields": [
                        {"name": "city", "type": "string" },
                        {"name": "street", "type": "Street" }
                    ]
                }""",
                listOf(SchemaReference("Street", "Street", 1))
            ).get()
        )

        folderRule.newFolder("src", "main", "avro", "external")
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": [ "null", "Address" ], "default": null}
             ]
            }
        """
        )

        val subjects = listOf(
            Subject(
                "test",
                "src/main/avro/external/test.avsc",
                "AVRO"
            )
                .addReference("Address", "Address", 1)
                .addReference("Street", "Street", 1)
        )

        // when
        val errorCount = CompatibilityTaskAction(
            registryClient,
            folderRule.root,
            subjects
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }


    @Test
    fun `Should verify compatibility with local and remote references`() {
        // given
        val registryClient = MockSchemaRegistryClient()
        registryClient.register(
            "test",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        // Register dependency
        registryClient.register(
            "Street",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Street",
                    "fields": [
                        {"name": "street", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        folderRule.newFolder("src", "main", "avro", "local")
        File(folderRule.root, "src/main/avro/local/country.avsc").writeText(
            """{
                    "type": "enum",
                    "name": "Country",
                    "symbols": [
                        "UNKNOWN",
                        "FR",
                        "DE",
                        "UK",
                        "IT",
                        "ES",
                        "US"
                  ],
                  "default": "UNKNOWN"
                }"""
        )
        File(folderRule.root, "src/main/avro/local/address.avsc").writeText(
            """{
                    "type": "record",
                    "name": "Address",
                    "fields": [
                        {"name": "city", "type": "string" },
                        {"name": "street", "type": "Street" },
                        {"name": "country", "type": "Country" }
                    ]
                }"""
        )

        File(folderRule.root, "src/main/avro/local/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": [ "null", "Address" ], "default": null}
             ]
            }
        """
        )

        val subjects = listOf(
            Subject(
                "test",
                "src/main/avro/local/test.avsc",
                "AVRO"
            )
                .addReference("Street", "Street", 1)
                .addLocalReference("Country", "src/main/avro/local/country.avsc")
                .addLocalReference("Address", "src/main/avro/local/address.avsc")
        )

        // when
        val errorCount = CompatibilityTaskAction(
            registryClient,
            folderRule.root,
            subjects
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should fail on incompatible schemas`() {
        // given
        val registryClient = MockSchemaRegistryClient(listOf())
        registryClient.register(
            "test",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "test",
                    "fields": [
                        { "name": "name", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )
        folderRule.newFolder("src", "main", "avro", "external")

        val subjects =
            arrayListOf(Subject("test", "src/main/avro/external/test.avsc", "AVRO"))
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "boolean" }
             ]
            }
        """
        )

        // when
        val errorCount = CompatibilityTaskAction(
            registryClient,
            folderRule.root,
            subjects,
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(1)
    }

    @Test
    fun `Should succeed if schema does not exist`() {
        // Given

        val spySchemaRegistry = spyk<MockSchemaRegistryClient>()
        every {
            spySchemaRegistry.testCompatibility(any(), any<ParsedSchema>())
        } throws RestClientException("Subject not found", 404, Errors.SUBJECT_NOT_FOUND_ERROR_CODE)

        folderRule.newFolder("src", "main", "avro", "external")

        val subjects = listOf(
            Subject(
                "test",
                "src/main/avro/external/test.avsc",
                "AVRO"
            )
        )
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "boolean" }
             ]
            }
        """
        )

        // when
        val errorCount = CompatibilityTaskAction(
            spySchemaRegistry,
            folderRule.root,
            subjects,
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }
}
