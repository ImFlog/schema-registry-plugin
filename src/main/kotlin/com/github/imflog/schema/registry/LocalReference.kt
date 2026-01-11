package com.github.imflog.schema.registry

import java.io.File
import java.io.Serializable

data class LocalReference(
    val name: String,
    val path: String
) : Serializable {
    fun content(rootDir: File) = rootDir.resolve(path).readText()
}
