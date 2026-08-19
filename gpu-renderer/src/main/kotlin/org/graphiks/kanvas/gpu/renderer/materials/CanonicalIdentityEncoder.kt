package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.collections.CanonicalIdentityDigestEncoder

/**
 * Canonical typed identity encoding.
 *
 * Every domain, field name, type, collection count and payload is
 * length-delimited before hashing, so caller text can contain arbitrary
 * separators without changing field boundaries.
 */
class CanonicalIdentityEncoder(
    domain: String,
) {
    private val delegate = CanonicalIdentityDigestEncoder(domain)

    fun text(name: String, value: String): CanonicalIdentityEncoder =
        apply { delegate.text(name, value) }

    fun bytes(name: String, value: ByteArray): CanonicalIdentityEncoder =
        apply { delegate.bytes(name, value) }

    fun int(name: String, value: Int): CanonicalIdentityEncoder =
        apply { delegate.int(name, value) }

    fun long(name: String, value: Long): CanonicalIdentityEncoder =
        apply { delegate.long(name, value) }

    fun floatBits(name: String, value: Float): CanonicalIdentityEncoder =
        apply { delegate.floatBits(name, value) }

    fun boolean(name: String, value: Boolean): CanonicalIdentityEncoder =
        apply { delegate.boolean(name, value) }

    fun texts(name: String, values: List<String>): CanonicalIdentityEncoder =
        apply { delegate.texts(name, values) }

    fun digestHex(): String = delegate.digestHex()

    fun digestIdentity(): String = delegate.digestIdentity()
}
