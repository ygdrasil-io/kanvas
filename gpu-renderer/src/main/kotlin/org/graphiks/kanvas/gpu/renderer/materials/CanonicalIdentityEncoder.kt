package org.graphiks.kanvas.gpu.renderer.materials

import java.security.MessageDigest

/**
 * Canonical typed identity encoding.
 *
 * Every domain, field name, type, collection count and payload is
 * length-delimited before hashing, so caller text can contain arbitrary
 * separators without changing field boundaries.
 */
internal class CanonicalIdentityEncoder(
    private val domain: String,
) {
    private val fields = mutableListOf<CanonicalIdentityField>()

    fun text(name: String, value: String): CanonicalIdentityEncoder =
        field(name, CanonicalIdentityFieldType.TEXT, value.encodeToByteArray())

    fun bytes(name: String, value: ByteArray): CanonicalIdentityEncoder =
        field(name, CanonicalIdentityFieldType.BYTES, value.copyOf())

    fun int(name: String, value: Int): CanonicalIdentityEncoder =
        field(name, CanonicalIdentityFieldType.INT32, value.canonicalBytes())

    fun long(name: String, value: Long): CanonicalIdentityEncoder =
        field(name, CanonicalIdentityFieldType.INT64, value.canonicalBytes())

    fun floatBits(name: String, value: Float): CanonicalIdentityEncoder =
        field(name, CanonicalIdentityFieldType.FLOAT32_BITS, value.toRawBits().canonicalBytes())

    fun boolean(name: String, value: Boolean): CanonicalIdentityEncoder =
        field(
            name,
            CanonicalIdentityFieldType.BOOLEAN,
            byteArrayOf(if (value) 1 else 0),
        )

    fun texts(name: String, values: List<String>): CanonicalIdentityEncoder {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(values.size.canonicalBytes())
        values.forEach { value ->
            val bytes = value.encodeToByteArray()
            digest.update(bytes.size.canonicalBytes())
            digest.update(bytes)
        }
        return field(
            name,
            CanonicalIdentityFieldType.TEXT_LIST,
            values.size.canonicalBytes() + digest.digest(),
        )
    }

    fun digestHex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(CANONICAL_IDENTITY_MAGIC)
        digest.updateLengthPrefixed(domain.encodeToByteArray())
        digest.update(fields.size.canonicalBytes())
        fields.forEach { field ->
            digest.updateLengthPrefixed(field.name.encodeToByteArray())
            digest.update(field.type.wireCode)
            digest.updateLengthPrefixed(field.payload)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun digestIdentity(): String = "sha256:${digestHex()}"

    private fun field(
        name: String,
        type: CanonicalIdentityFieldType,
        payload: ByteArray,
    ): CanonicalIdentityEncoder {
        require(name.isNotBlank()) { "Canonical identity field name must not be blank" }
        fields += CanonicalIdentityField(name, type, payload)
        return this
    }
}

private data class CanonicalIdentityField(
    val name: String,
    val type: CanonicalIdentityFieldType,
    val payload: ByteArray,
)

private enum class CanonicalIdentityFieldType(val wireCode: Byte) {
    TEXT(1),
    BYTES(2),
    INT32(3),
    INT64(4),
    FLOAT32_BITS(5),
    BOOLEAN(6),
    TEXT_LIST(7),
}

private fun MessageDigest.updateLengthPrefixed(bytes: ByteArray) {
    update(bytes.size.canonicalBytes())
    update(bytes)
}

private fun Int.canonicalBytes(): ByteArray = byteArrayOf(
    (this ushr 24).toByte(),
    (this ushr 16).toByte(),
    (this ushr 8).toByte(),
    toByte(),
)

private fun Long.canonicalBytes(): ByteArray = byteArrayOf(
    (this ushr 56).toByte(),
    (this ushr 48).toByte(),
    (this ushr 40).toByte(),
    (this ushr 32).toByte(),
    (this ushr 24).toByte(),
    (this ushr 16).toByte(),
    (this ushr 8).toByte(),
    toByte(),
)

private val CANONICAL_IDENTITY_MAGIC =
    "kanvas-canonical-identity-v1".encodeToByteArray()
