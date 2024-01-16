package com.github.imflog.schema.registry

import java.io.File

data class LocalReference(
    val name: String,
    val path: String
) {
    fun content(rootDir: File) = rootDir.resolve(path).readText()
}
