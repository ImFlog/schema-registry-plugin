package com.github.imflog.schema.registry.register

open class RegisterExtension {

    val subjects: ArrayList<Pair<String, String>> = ArrayList()

    fun subject(inputSubject: String, file: String) = subjects + Pair(inputSubject, file)
}
