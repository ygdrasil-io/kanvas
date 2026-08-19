package org.graphiks.kanvas.gpu.renderer.collections

import java.security.MessageDigest

/**
 * Module-internal typed identity encoding shared by authorities that cannot
 * depend on one another's packages.
 */
internal class CanonicalIdentityDigestEncoder(
    private val domain: String,
) {
    private val fields = mutableListOf<CanonicalIdentityDigestField>()

    fun text(name: String, value: String): CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.TEXT, value.encodeToByteArray())

    fun bytes(name: String, value: ByteArray): CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.BYTES, value.copyOf())

    fun int(name: String, value: Int): CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.INT32, value.canonicalIdentityBytes())

    fun long(name: String, value: Long): CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.INT64, value.canonicalIdentityBytes())

    fun floatBits(name: String, value: Float): CanonicalIdentityDigestEncoder =
        field(
            name,
            CanonicalIdentityDigestFieldType.FLOAT32_BITS,
            value.toRawBits().canonicalIdentityBytes(),
        )

    fun boolean(name: String, value: Boolean): CanonicalIdentityDigestEncoder =
        field(
            name,
            CanonicalIdentityDigestFieldType.BOOLEAN,
            byteArrayOf(if (value) 1 else 0),
        )

    fun texts(name: String, values: List<String>): CanonicalIdentityDigestEncoder {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(values.size.canonicalIdentityBytes())
        values.forEach { value ->
            val bytes = value.encodeToByteArray()
            digest.update(bytes.size.canonicalIdentityBytes())
            digest.update(bytes)
        }
        return field(
            name,
            CanonicalIdentityDigestFieldType.TEXT_LIST,
            values.size.canonicalIdentityBytes() + digest.digest(),
        )
    }

    fun digestHex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(CANONICAL_IDENTITY_MAGIC)
        digest.updateCanonicalLengthPrefixed(domain.encodeToByteArray())
        digest.update(fields.size.canonicalIdentityBytes())
        fields.forEach { field ->
            digest.updateCanonicalLengthPrefixed(field.name.encodeToByteArray())
            digest.update(field.type.wireCode)
            digest.updateCanonicalLengthPrefixed(field.payload)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun digestIdentity(): String = "sha256:${digestHex()}"

    private fun field(
        name: String,
        type: CanonicalIdentityDigestFieldType,
        payload: ByteArray,
    ): CanonicalIdentityDigestEncoder {
        require(name.isNotBlank()) { "Canonical identity field name must not be blank" }
        fields += CanonicalIdentityDigestField(name, type, payload)
        return this
    }
}

/** Versioned exact UTF-16 code-unit encoder used only by new Task 7 identities. */
internal class ExactUtf16CanonicalIdentityDigestEncoder(
    private val domain: String,
) {
    private val fields = mutableListOf<CanonicalIdentityDigestField>()

    fun text(name: String, value: String): ExactUtf16CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.TEXT, value.exactUtf16CodeUnits())

    fun bytes(name: String, value: ByteArray): ExactUtf16CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.BYTES, value.copyOf())

    fun int(name: String, value: Int): ExactUtf16CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.INT32, value.canonicalIdentityBytes())

    fun floatBits(name: String, value: Float): ExactUtf16CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.FLOAT32_BITS, value.toRawBits().canonicalIdentityBytes())

    fun boolean(name: String, value: Boolean): ExactUtf16CanonicalIdentityDigestEncoder =
        field(name, CanonicalIdentityDigestFieldType.BOOLEAN, byteArrayOf(if (value) 1 else 0))

    fun texts(name: String, values: List<String>): ExactUtf16CanonicalIdentityDigestEncoder {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(values.size.canonicalIdentityBytes())
        values.forEach { value ->
            val bytes = value.exactUtf16CodeUnits()
            digest.update(bytes.size.canonicalIdentityBytes())
            digest.update(bytes)
        }
        return field(
            name,
            CanonicalIdentityDigestFieldType.TEXT_LIST,
            values.size.canonicalIdentityBytes() + digest.digest(),
        )
    }

    fun digestIdentity(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(EXACT_UTF16_CANONICAL_IDENTITY_MAGIC)
        digest.updateCanonicalLengthPrefixed(domain.exactUtf16CodeUnits())
        digest.update(fields.size.canonicalIdentityBytes())
        fields.forEach { field ->
            digest.updateCanonicalLengthPrefixed(field.name.exactUtf16CodeUnits())
            digest.update(field.type.wireCode)
            digest.updateCanonicalLengthPrefixed(field.payload)
        }
        return "sha256:" + digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun field(
        name: String,
        type: CanonicalIdentityDigestFieldType,
        payload: ByteArray,
    ): ExactUtf16CanonicalIdentityDigestEncoder {
        require(name.isNotBlank()) { "Canonical identity field name must not be blank" }
        fields += CanonicalIdentityDigestField(name, type, payload)
        return this
    }
}

private data class CanonicalIdentityDigestField(
    val name: String,
    val type: CanonicalIdentityDigestFieldType,
    val payload: ByteArray,
)

private enum class CanonicalIdentityDigestFieldType(val wireCode: Byte) {
    TEXT(1),
    BYTES(2),
    INT32(3),
    INT64(4),
    FLOAT32_BITS(5),
    BOOLEAN(6),
    TEXT_LIST(7),
}

private fun MessageDigest.updateCanonicalLengthPrefixed(bytes: ByteArray) {
    update(bytes.size.canonicalIdentityBytes())
    update(bytes)
}

private fun Int.canonicalIdentityBytes(): ByteArray = byteArrayOf(
    (this ushr 24).toByte(),
    (this ushr 16).toByte(),
    (this ushr 8).toByte(),
    toByte(),
)

private fun Long.canonicalIdentityBytes(): ByteArray = byteArrayOf(
    (this ushr 56).toByte(),
    (this ushr 48).toByte(),
    (this ushr 40).toByte(),
    (this ushr 32).toByte(),
    (this ushr 24).toByte(),
    (this ushr 16).toByte(),
    (this ushr 8).toByte(),
    toByte(),
)

/** Exact UTF-16BE code-unit encoding; isolated surrogates are preserved, never replaced. */
private fun String.exactUtf16CodeUnits(): ByteArray = ByteArray(length * 2).also { bytes ->
    forEachIndexed { index, codeUnit ->
        bytes[index * 2] = (codeUnit.code ushr 8).toByte()
        bytes[index * 2 + 1] = codeUnit.code.toByte()
    }
}

private val CANONICAL_IDENTITY_MAGIC =
    "kanvas-canonical-identity-v1".encodeToByteArray()

private val EXACT_UTF16_CANONICAL_IDENTITY_MAGIC =
    byteArrayOf(0x4b, 0x43, 0x49, 0x44, 0x02, 0x55, 0x54, 0x46, 0x16)
