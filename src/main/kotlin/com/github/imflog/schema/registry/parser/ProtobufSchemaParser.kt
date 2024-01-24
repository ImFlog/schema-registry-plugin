package com.github.imflog.schema.registry.parser

import com.github.imflog.schema.registry.LocalReference
import com.github.imflog.schema.registry.SchemaType
import com.google.common.collect.Iterables
import com.squareup.wire.schema.*
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import okio.FileSystem
import org.slf4j.LoggerFactory
import java.io.File

class ProtobufSchemaParser(
    client: SchemaRegistryClient,
    rootDir: File
) : SchemaParser(client, rootDir) {
    override val schemaType: SchemaType = SchemaType.PROTOBUF

    override fun resolveLocalReferences(
        subject: String,
        schemaPath: String,
        localReferences: List<LocalReference>
    ): String {
        val schema = schemaFor(rootDir)
        val source = schema.protoFile(File(schemaPath).relativeTo(rootDir).path)!!
        val refs: Map<String, File> = parseRefs(localReferences)

        return LocalReferenceTransformer(rootDir, schema, refs).transform(source)
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

    class LocalReferenceTransformer(
        private val rootDir: File,
        private val schema: Schema,
        private val refs: Map<String, File>
    ) {
        private val log = LoggerFactory.getLogger(LocalReferenceTransformer::class.java)

        fun transform(source: ProtoFile): String {
            val hierarchy = DependencyHierarchy(source, schema)

            val (filesToFlatten, filesToRetain) = hierarchy.partition {
                val import = it.location.path

                // Unknown local reference
                val ref = refs[import]
                if (ref == null && it != source) { // Source ends up in the filesToFlatten even if it was in the local references
                    if (!isBuiltInImport(import)) {
                        // It's normal to ignore built-ins, so no warning in that case.
                        log.warn(
                            "Unknown reference '{}' encountered while processing local references, it will be retained as is. Known references: {}",
                            import, refs
                        )
                    }
                    false
                } else {
                    true
                }
            }

            val typesToRetain = filesToFlatten.flatMap { file ->
                val import = file.location.path
                val ref = refs[import]

                // This would normally resolve into the same exact file as the reference itself,
                // but the way LocalReference is constructed implies it could be elsewhere, so we'll have to
                // follow through with the API.
                val dependency = if (ref != null) {
                    schema.protoFile(ref.relativeTo(rootDir).path)
                        ?: throw RuntimeException("Dependency not found for local reference $import at ${ref.absolutePath}")
                } else {
                    file
                }
                dependency.types.map { standardizeNames(it, dependency) }
            }

            val result = source.copy(
                imports = filesToRetain.map { it.location.path }.toList(),
                publicImports = emptyList(),
                types = typesToRetain.toList(),
                extendList = source.extendList.map { standardizeNames(it, source) },
                services = source.services.map { standardizeNames(it, source) },
            ).toSchema()

            log.info(
                "Local reference schema conversion for {}:\n{}\nto:\n{}",
                source.location.path,
                source.toSchema(),
                result
            )

            return result
        }

        private fun isBuiltInImport(import: String): Boolean {
            return import.startsWith("google/protobuf")
        }

        private fun standardizeNames(protoType: ProtoType, file: ProtoFile): ProtoType {
            return when {
                protoType.isScalar -> protoType
                protoType.isMap -> {
                    val newKey = standardizeNames(protoType.keyType!!, file)
                    val newValue = standardizeNames(protoType.valueType!!, file)
                    ProtoType.get(
                        keyType = newKey,
                        valueType = newValue,
                        name = "map<$newKey, $newValue>"
                    )
                }

                isLocalReference(protoType, file) -> ProtoType.get(protoType.simpleName)
                else -> protoType // Not in a local reference, keep as is
            }
        }

        private fun standardizeNames(type: Type, file: ProtoFile): Type {
            return when (type) {
                is EnclosingType -> type.copy(nestedTypes = type.nestedTypes.map { standardizeNames(it, file) })
                is MessageType -> type.copy(
                    declaredFields = type.declaredFields.map { standardizeNames(it, file) },
                    extensionFields = type.extensionFields.map { standardizeNames(it, file) }.toMutableList(),
                    oneOfs = type.oneOfs.map { standardizeNames(it, file) },
                    nestedTypes = type.nestedTypes.map { standardizeNames(it, file) },
                    nestedExtendList = type.nestedExtendList.map { standardizeNames(it, file) },
                    extensionsList = type.extensionsList.map { standardizeNames(it, file) }
                )

                is EnumType -> type // Keep it as is
            }
        }

        private fun standardizeNames(oneOf: OneOf, file: ProtoFile): OneOf {
            return oneOf.copy(fields = oneOf.fields.map { standardizeNames(it, file) })
        }

        private fun standardizeNames(field: Field, file: ProtoFile): Field {
            val newType = standardizeNames(field.type!!, file)
            return field.copy(elementType = newType.toString())
        }

        @Suppress("UNUSED_PARAMETER")
        private fun standardizeNames(extensions: Extensions, file: ProtoFile): Extensions {
            return extensions // Extension overwrite is not supported
        }

        @Suppress("UNUSED_PARAMETER")
        private fun standardizeNames(extend: Extend, file: ProtoFile): Extend {
            // We're not supporting them for now, until we have a clear use case.
            return extend
        }

        @Suppress("UNUSED_PARAMETER")
        private fun standardizeNames(service: Service, file: ProtoFile): Service {
            // RPC rewriting is not supported. RPCs are not fully parsed by Wire, so getting them
            // supported would be a lot of work.
            return service
        }

        private fun isLocalReference(type: ProtoType, file: ProtoFile): Boolean {
            val import = findSourceImport(type, file)
            return refs.contains(import)
        }

        private fun findSourceImport(type: ProtoType, root: ProtoFile): String? {
            val containingFile = DependencyHierarchy(root, schema).find { file ->
                file.typesAndNestedTypes()
                    .map { it.type }
                    .contains(type)
            }
            return containingFile?.location?.path
        }
    }

    class DependencyHierarchy(private val root: ProtoFile, private val schema: Schema) : Iterable<ProtoFile> {
        override fun iterator(): Iterator<ProtoFile> {
            return DependencyHierarchyIterator(root, schema)
        }
    }

    class DependencyHierarchyIterator(private val root: ProtoFile, schema: Schema) : Iterator<ProtoFile> {
        private var rootReturned = false
        private val delegate = ImportsIterator(schema, root.imports + root.publicImports)
        override fun hasNext(): Boolean {
            return !rootReturned || delegate.hasNext()
        }

        override fun next(): ProtoFile {
            return if (!rootReturned) {
                rootReturned = true
                root
            } else {
                delegate.next()
            }
        }
    }

    class ImportsIterator(private val schema: Schema, imports: Collection<String>) : Iterator<ProtoFile> {

        private val imports = ArrayDeque(imports)
        private val processedImports = mutableSetOf<String>()

        override fun hasNext(): Boolean {
            return imports.isNotEmpty()
        }

        override fun next(): ProtoFile {
            val current = imports.removeFirstOrNull() ?: throw NoSuchElementException("No more imports")
            val result =
                schema.protoFile(current) ?: throw IllegalStateException("Import '$current' is not in the schema path")
            addAll(result.imports)
            addAll(result.publicImports)
            return result
        }

        private fun addAll(newImports: Iterable<String>) {
            newImports.filter(processedImports::add).forEach(imports::addLast)
        }
    }
}
