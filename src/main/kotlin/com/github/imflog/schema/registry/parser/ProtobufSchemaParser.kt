package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import com.squareup.wire.schema.*
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import okio.FileSystem
import org.slf4j.LoggerFactory
import java.io.File

class ProtobufSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {
    private val log = LoggerFactory.getLogger(ProtobufSchemaParser::class.java)
    private val maxRefs = 100_000

    override val schemaType: SchemaType = SchemaType.PROTOBUF

    override fun resolveLocalReferences(
        subject: String,
        schemaPath: String,
        localReferences: List<LocalReference>
    ): String {
        val schema = schemaFor(rootDir)

        val source = schema.protoFile(File(schemaPath).relativeTo(rootDir).path)!!
        // Keeping track of what types we've already found in order to fail-fast
        // duplicate definitions before they reach the Registry.
        val flattenedTypes = source.types.associateBy {
            it.name
        }.toMutableMap()
        val importsProcessed = mutableSetOf<String>()

        val typesToRetain = source.types.map(this::standardizeNames).toMutableList()
        val importsToRetain = mutableSetOf<String>()

        // Their publicity doesn't matter for the purposes of ref unpacking
        val importsToProcess = ArrayDeque<String>()
        importsToProcess.addAll(source.imports)
        importsToProcess.addAll(source.publicImports)

        val refs: Map<String, File> = parseRefs(localReferences)

        // To make sure it won't infinite-loop our CI into oblivion
        for (i in 0..maxRefs) {
            if (i == maxRefs - 1) {
                throw RuntimeException("Timed out processing imports. Is there a dependency loop?")
            }

            val import = importsToProcess.removeFirstOrNull()
                ?: break

            // Built-ins
            if (isBuiltInImport(import)) {
                importsToRetain.add(import)
                continue
            }

            // Unknown local reference
            val ref = refs[import]
            if (ref == null) {
                log.warn(
                    "Unknown reference '{}' encountered while processing local references, it will be retained as is. Known references: {}",
                    import, refs
                )
                importsToRetain.add(import)
                continue
            }

            // Duplicate imports are ignored
            //
            // Always resolving imports from the Subject's file, to make sure we catch
            // different relative imports of the same file.
            val normalizedPath = ref.relativeTo(rootDir).normalize().path
            if (!importsProcessed.add(normalizedPath)) {
                continue
            }

            // No more excuses, we're processing this dependency.
            val dependency = schema.protoFile(ref.relativeTo(rootDir).path)
                ?: throw RuntimeException("Dependency not found for local reference $import at ${ref.absolutePath}")

            importsToProcess.addAll(dependency.imports)
            importsToProcess.addAll(dependency.publicImports)

            for (type in dependency.types) {
                val existingType = flattenedTypes.putIfAbsent(type.name, type)
                if (existingType != null) {
                    log.error(
                        "Duplicate imports found for the same type {}: {} -> {}",
                        type.name,
                        existingType.location.path,
                        type.location.path
                    )
                    throw RuntimeException("Duplicate imports in local references are not supported")
                }

                typesToRetain.add(standardizeNames(type))
            }
        }

        val result = source.copy(
            imports = importsToRetain.toList(),
            publicImports = emptyList(),
            types = typesToRetain.toList(),
            extendList = source.extendList.map(this::standardizeNames),
            services = source.services.map(this::standardizeNames),
        ).toSchema()

        log.info("Local reference schema conversion for {}:\n{}\nto:\n{}", subject, source.toSchema(), result)

        return result
    }

    private fun schemaFor(schemaDirectory: File): Schema {
        val loader = loaderFor(schemaDirectory)
        return loader.loadSchema()
    }

    private fun loaderFor(schemaDirectory: File): SchemaLoader {
        val loader = SchemaLoader(FileSystem.SYSTEM)
        loader.initRoots(
            listOf(Location.get(schemaDirectory.absolutePath)),
            listOf(Location.get(schemaDirectory.absolutePath))
        )
        return loader
    }

    private fun parseRefs(localReferences: List<LocalReference>): Map<String, File> {
        return localReferences.associate {
            Pair(it.name, File(it.path).normalize())
        }
    }

    private fun isBuiltInImport(import: String): Boolean {
        return import.startsWith("google/protobuf")
    }

    private fun isBuiltInPackage(type: ProtoType): Boolean {
        return type.enclosingTypeOrPackage?.startsWith("google.protobuf") ?: false
    }

    private fun standardizeNames(protoType: ProtoType): ProtoType {
        return when {
            protoType.isScalar -> protoType
            protoType.isMap -> {
                val newKey = standardizeNames(protoType.keyType!!)
                val newValue = standardizeNames(protoType.valueType!!)
                ProtoType.get(
                    keyType = newKey,
                    valueType = newValue,
                    name = "map<$newKey, $newValue>"
                )
            }

            isBuiltInPackage(protoType) -> protoType
            else /* It's a custom message/enum, it must be overwritten */ -> ProtoType.get(protoType.simpleName) // Removing package prefix
        }
    }

    private fun standardizeNames(type: Type): Type {
        return when (type) {
            is EnclosingType -> type.copy(nestedTypes = type.nestedTypes.map(this::standardizeNames))
            is MessageType -> type.copy(
                declaredFields = type.declaredFields.map(this::standardizeNames),
                extensionFields = type.extensionFields.map(this::standardizeNames).toMutableList(),
                oneOfs = type.oneOfs.map(this::standardizeNames),
                nestedTypes = type.nestedTypes.map(this::standardizeNames),
                nestedExtendList = type.nestedExtendList.map(this::standardizeNames),
                extensionsList = type.extensionsList.map(this::standardizeNames)
            )

            is EnumType -> type // Keep it as is
        }
    }

    private fun standardizeNames(oneOf: OneOf): OneOf {
        return oneOf.copy(fields = oneOf.fields.map(this::standardizeNames))
    }

    private fun standardizeNames(field: Field): Field {
        val newType = standardizeNames(field.type!!)
        return field.copy(elementType = newType.toString())
    }

    private fun standardizeNames(extensions: Extensions): Extensions {
        return extensions // Extension overwrite is not supported
    }

    private fun standardizeNames(extend: Extend): Extend {
        // We're not supporting them for now, until we have a clear use case.
        return extend
    }

    private fun standardizeNames(service: Service): Service {
        // RPC rewriting is not supported. RPCs are not fully parsed by Wire, so getting them
        // supported would be a lot of work.
        return service
    }
}
