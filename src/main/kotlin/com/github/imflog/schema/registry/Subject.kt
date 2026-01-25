package com.github.imflog.schema.registry

import com.google.gson.Gson
import io.confluent.kafka.schemaregistry.client.rest.entities.Metadata
import io.confluent.kafka.schemaregistry.client.rest.entities.RuleSet
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File
import java.io.Serializable

data class Subject(
    val inputSubject: String,
    val file: String,
    val type: String
) : Serializable {
    @Transient
    val references: MutableList<SchemaReference> = mutableListOf()
    val localReferences: MutableList<LocalReference> = mutableListOf()
    @Transient
    var metadata: Metadata? = null
    @Transient
    var ruleSet: RuleSet? = null
    var normalize: Boolean = false
    private var metadataPath: String? = null
    private var ruleSetPath: String? = null

    fun addReference(name: String, subject: String, version: Int): Subject {
        references.add(SchemaReference(name, subject, version))
        return this
    }

    fun addReference(name: String, subject: String): Subject {
        references.add(SchemaReference(name, subject, -1))
        return this
    }

    fun addLocalReference(name: String, path: String): Subject {
        localReferences.add(LocalReference(name, path))
        return this
    }

    fun setMetadata(path: String): Subject {
        metadataPath = path
        return this
    }

    fun setRuleSet(path: String): Subject {
        ruleSetPath = path
        return this
    }

    fun resolveMetadata(rootDir: File) {
        metadataPath?.let {
            val metadataContent = rootDir.resolve(it).readText(Charsets.UTF_8)
            metadata = Gson().fromJson(metadataContent, Metadata::class.java)
        }
        ruleSetPath?.let {
            val ruleSetContent = rootDir.resolve(it).readText(Charsets.UTF_8)
            ruleSet = Gson().fromJson(ruleSetContent, RuleSet::class.java)
        }
    }

    fun setNormalized(normalize: Boolean): Subject {
        this.normalize = normalize
        return this
    }
}
