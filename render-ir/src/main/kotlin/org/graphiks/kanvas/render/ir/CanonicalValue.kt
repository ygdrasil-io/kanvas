package org.graphiks.kanvas.render.ir

import java.util.Collections
import java.util.LinkedHashMap
import java.util.TreeMap

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

internal fun canonicalId(tag: String, vararg fields: String): CanonicalId = CanonicalId(
    buildString {
        append(tag.length).append(':').append(tag)
        fields.forEach { field -> append('|').append(field.length).append(':').append(field) }
    },
)

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
