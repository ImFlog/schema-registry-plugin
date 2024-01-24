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
                dependency.types.map {
                    standardizeNames(it, dependency)
                }
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

                isLocalReference(protoType, file) -> trimToLocalName(protoType, findSource(protoType, file))
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
            // This is a somewhat unfortunate hack. We had problems with our Schema Registry when mixing outer-scope
            // package references (.some.package.Message) and standard package imports (some.package.Message).
            // `Field.elementType` contains the actual parsed type, and, unlike `Field.type`, it preserves
            // the leading dot. Unfortunately, it's private within `Field`, so the only option for accessing it is
            // by converting it first into its corresponding Element.
            val fieldElement = Field.toElements(listOf(field)).first()
            val fieldType = ProtoType.get(fieldElement.type)
            val newType = standardizeNames(fieldType, file)
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
            val import = findSource(type, file)?.location?.path
            return refs.contains(import)
        }

        private fun findSource(type: ProtoType, root: ProtoFile): ProtoFile? {
            val typeVariants = toTypeVariants(type, root)
                // Wire loses the leading . on linking, making `Field.type="package.Type"` not equal
                // `".package.Type"`.
                .map { ProtoType.get(it.toString().removePrefix(".")) }
                .toSet()
            val containingFile = DependencyHierarchy(root, schema).find { file ->
                val allTypes = file.typesAndNestedTypes()
                    .map { it.type }
                    .toSet()
                allTypes.intersect(typeVariants).isNotEmpty()
            }
            return containingFile
        }

        private fun toTypeVariants(type: ProtoType, source: ProtoFile): List<ProtoType> {
            val packageName = source.packageName
            return when {
                type.toString().startsWith(".") -> {
                    // Absolute import
                    listOf(type)
                }

                packageName != null -> {
                    // for type T and package segments "a", "b" it would produce "T", "b.T", "a.b.T"

                    val prefixes = packageName.split('.')
                        .runningFold("") { acc, segment ->
                            when (acc) {
                                "" -> segment
                                else -> "${acc}.${segment}"
                            }
                        }

                    prefixes
                        .map { ProtoType.get("${it}.${type}") }
                        .toList()
                }

                else -> {
                    // Top level package already
                    listOf(type)

                }
            }
        }

        private fun trimToLocalName(type: ProtoType, source: ProtoFile?): ProtoType {
            if (source == null) {
                return type
            }
            val withoutDot = type.toString()
                // Handle absolute package names (the post-linkage `Field.type` doesn't have those,
                // so it'll never match unless we strip the dot).
                .removePrefix(".")
            val fullType = source.typesAndNestedTypes().find { it.type.toString().endsWith(withoutDot) }
                ?: throw RuntimeException("Type $type could not be found in ${source.location.path}")
            val relativePackageString = fullType.type.toString()
                // Strip the package -> make it a "local" reference.
                .removePrefix((source.packageName ?: "") + ".")
            return ProtoType.get(relativePackageString)
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
                schema.protoFile(current)
                    ?: throw IllegalStateException("Import '$current' is not in the schema path")
            addAll(result.imports)
            addAll(result.publicImports)
            return result
        }

        private fun addAll(newImports: Iterable<String>) {
            newImports.filter(processedImports::add).forEach(imports::addLast)
        }
    }
}
