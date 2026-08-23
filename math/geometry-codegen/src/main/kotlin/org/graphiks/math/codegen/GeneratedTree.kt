package org.graphiks.math.codegen

internal data class GeneratedFile(val relativePath: String, val utf8: ByteArray)

internal data class GeneratedTree(val files: List<GeneratedFile>) {
    init {
        files.groupingBy { it.relativePath }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .firstOrNull()
            ?.let { duplicate ->
                throw IllegalArgumentException("generated path is duplicated: $duplicate")
            }
    }
}
