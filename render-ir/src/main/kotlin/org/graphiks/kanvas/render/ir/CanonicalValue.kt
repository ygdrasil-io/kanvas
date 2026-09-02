package org.graphiks.kanvas.render.ir

import java.util.Collections
import java.util.LinkedHashMap
import java.util.TreeMap
import java.security.MessageDigest

/** A stable content identity for a backend-neutral value. */
@JvmInline
public value class CanonicalId(public val value: String) {
    init {
        require(value.isNotBlank()) { "CanonicalId.value must not be blank" }
    }
}

/** A value whose identity depends only on its public rendering semantics. */
public interface CanonicalValue {
    public val canonicalId: CanonicalId
}

/** Encodes a scene independently of a particular renderer or native resource. */
public object CanonicalSceneEncoder {
    public fun encode(scene: SceneSnapshot): CanonicalId = canonicalId(
        "scene-v1",
        scene.extent.canonicalId.value,
        scene.colorSpace.name,
        scene.colorSpace.transferFunction.name,
        scene.colorSpace.gamut.name,
        canonicalSequenceId("commands", scene.map { it.canonicalId.value }).value,
    )
}

/**
 * Produces a fixed-size identity from length-delimited UTF-16 code-unit fields.
 *
 * The explicit format domain, tag, field count, and byte lengths preserve the
 * former field-boundary semantics, including isolated surrogate code units,
 * without recursively embedding whole child identities in their parents.
 */
internal fun canonicalId(tag: String, vararg fields: String): CanonicalId {
    val digest = MessageDigest.getInstance("SHA-256")
    canonicalDigestField(digest, "kanvas-canonical-id-v3")
    canonicalDigestField(digest, tag)
    canonicalDigestLength(digest, fields.size)
    fields.forEach { canonicalDigestField(digest, it) }
    return CanonicalId(canonicalHex(digest.digest()))
}

private fun canonicalDigestField(digest: MessageDigest, value: String) {
    canonicalDigestLength(digest, value.length)
    value.forEach { codeUnit ->
        digest.update((codeUnit.code ushr 8).toByte())
        digest.update(codeUnit.code.toByte())
    }
}

private fun canonicalDigestLength(digest: MessageDigest, value: Int) {
    digest.update((value ushr 24).toByte())
    digest.update((value ushr 16).toByte())
    digest.update((value ushr 8).toByte())
    digest.update(value.toByte())
}

private fun canonicalHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    bytes.forEach { byte ->
        val value = byte.toInt() and 0xff
        append(CANONICAL_HEX[value ushr 4])
        append(CANONICAL_HEX[value and 0x0f])
    }
}

private const val CANONICAL_HEX: String = "0123456789abcdef"

/** Uses Kotlin's canonical floating-point equality bits in canonical identities. */
internal fun Float.canonicalBits(): String = toBits().toString()

/** Freezes a caller-owned collection behind a JVM-unmodifiable view. */
internal fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

/** Freezes caller-owned map entries in their canonical key order. */
internal fun <K : Comparable<K>, V> immutableSortedMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(TreeMap(values))

/** Freezes caller-owned map entries while retaining their source iteration order. */
internal fun <K, V> immutableInsertionOrderMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))

/** Encodes a collection as a nested canonical value, retaining its cardinality and field boundaries. */
internal fun canonicalSequenceId(tag: String, values: Collection<String>): CanonicalId =
    canonicalId(tag, values.size.toString(), *values.toTypedArray())

/** Encodes a nullable canonical subvalue without conflating absence and an empty value. */
internal fun canonicalOptionalId(tag: String, value: CanonicalId?): CanonicalId = canonicalId(
    tag,
    if (value == null) "absent" else "present",
    value?.value.orEmpty(),
)
