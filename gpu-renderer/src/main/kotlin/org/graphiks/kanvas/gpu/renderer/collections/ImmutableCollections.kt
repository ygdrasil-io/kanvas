package org.graphiks.kanvas.gpu.renderer.collections

import java.util.Collections

/** Copies values and exposes a JVM collection that rejects mutation through a downcast. */
internal fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

/** Copies entries in insertion order and rejects mutation through a JVM downcast. */
internal fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))

/** Copies values in iteration order and rejects mutation through a JVM downcast. */
internal fun <T> immutableSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
