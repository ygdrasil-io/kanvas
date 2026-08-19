package org.graphiks.kanvas.surface.gpu

import java.security.MessageDigest

/**
 * Typed, length-delimited SHA-256 preimage encoder for exact prepared identities.
 *
 * Every value is paired with an explicit field name and type. This avoids
 * delimiter collisions and keeps identity code independent of mutable DTO
 * `toString()` implementations.
 */
internal class GPUCanonicalIdentityEncoder(schema: String) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var finished = false

    init {
        string("schema", schema)
    }

    fun boolean(name: String, value: Boolean) {
        field(name, TYPE_BOOLEAN)
        digest.update(if (value) 1.toByte() else 0.toByte())
    }

    fun int(name: String, value: Int) {
        field(name, TYPE_INT)
        updateInt(value)
    }

    fun float(name: String, value: Float) {
        field(name, TYPE_FLOAT)
        updateInt(value.toRawBits())
    }

    fun string(name: String, value: String) {
        field(name, TYPE_STRING)
        val bytes = value.toByteArray(Charsets.UTF_8)
        updateInt(bytes.size)
        digest.update(bytes)
    }

    fun unsignedBytes(name: String, values: List<Int>) {
        field(name, TYPE_BYTES)
        updateInt(values.size)
        values.forEach { value ->
            require(value in 0..255) { "$name must contain unsigned bytes" }
            digest.update(value.toByte())
        }
    }

    fun finishSha256(): String {
        check(!finished) { "Canonical identity encoder is already finished" }
        finished = true
        val bytes = digest.digest()
        val hex = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            hex[index * 2] = HEX[value ushr 4]
            hex[index * 2 + 1] = HEX[value and 0x0f]
        }
        return hex.concatToString()
    }

    private fun field(name: String, type: Byte) {
        check(!finished) { "Canonical identity encoder is already finished" }
        require(name.isNotBlank()) { "Canonical identity field name must not be blank" }
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        updateInt(nameBytes.size)
        digest.update(nameBytes)
        digest.update(type)
    }

    private fun updateInt(value: Int) {
        digest.update((value ushr 24).toByte())
        digest.update((value ushr 16).toByte())
        digest.update((value ushr 8).toByte())
        digest.update(value.toByte())
    }

    private companion object {
        const val TYPE_BOOLEAN: Byte = 1
        const val TYPE_INT: Byte = 2
        const val TYPE_FLOAT: Byte = 3
        const val TYPE_STRING: Byte = 4
        const val TYPE_BYTES: Byte = 5
        val HEX: CharArray = "0123456789abcdef".toCharArray()
    }
}

internal fun preparedStrokeGeometryPathKey(
    vertices: List<Float>,
    contourStarts: List<Int>,
): String {
    val encoder = GPUCanonicalIdentityEncoder("prepared-text-stroke-geometry:v1")
    encoder.int("vertexCount", vertices.size / 2)
    encoder.int("contourCount", contourStarts.size)
    contourStarts.forEachIndexed { index, contourStart ->
        encoder.int("contourStart[$index]", contourStart)
    }
    vertices.forEachIndexed { index, coordinate ->
        encoder.float("coordinate[$index]", coordinate)
    }
    return "prepared-text-stroke:sha256:${encoder.finishSha256()}"
}
