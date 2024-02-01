package com.github.imflog.schema.registry

import com.google.gson.Gson
import io.confluent.kafka.schemaregistry.client.rest.entities.Metadata
import io.confluent.kafka.schemaregistry.client.rest.entities.RuleSet
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import java.io.File

data class Subject(
    val inputSubject: String,
    val file: String,
    val type: String
) {
    val references: MutableList<SchemaReference> = mutableListOf()
    val localReferences: MutableList<LocalReference> = mutableListOf()
    var metadata: Metadata = Metadata(mutableMapOf(), mutableMapOf(), mutableSetOf())
    var ruleSet: RuleSet = RuleSet(mutableListOf(), mutableListOf());


    fun addReference(name: String, subject: String, version: Int): Subject {
        references.add(SchemaReference(name, subject, version))
        return this
    }

    fun addReference(name: String, subject:String): Subject {
        references.add(SchemaReference(name, subject, -1))
        return this
    }

    fun addLocalReference(name: String, path: String): Subject {
        localReferences.add(LocalReference(name, path))
        return this
    }

    fun setMetadata(path: String): Subject {
        val metadataContent = File(path).readText(Charsets.UTF_8)
        metadata =  Gson().fromJson(metadataContent, Metadata::class.java)
        return this
    }

    fun setRuleSet(path: String): Subject {
        val ruleSetContent = File(path).readText(Charsets.UTF_8)
        ruleSet = Gson().fromJson(ruleSetContent, RuleSet::class.java)
        return this
    }

    // Used for data class destructuring
    operator fun component4() = references
    operator fun component5() = localReferences
    operator fun component6() = metadata
    operator fun component7() = ruleSet
}
