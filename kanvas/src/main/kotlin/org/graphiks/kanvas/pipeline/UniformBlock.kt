package org.graphiks.kanvas.pipeline

import java.util.Collections
import org.graphiks.kanvas.types.Matrix33

class UniformBlock private constructor(
    entries: Map<String, UniformValue>,
) {
    val entries: Map<String, UniformValue> = Collections.unmodifiableMap(
        LinkedHashMap(entries.mapValues { (_, value) -> value.snapshotForUniformBlock() }),
    )

    companion object {
        val EMPTY = UniformBlock(emptyMap())
        operator fun invoke(block: UniformBlockScope.() -> Unit): UniformBlock {
            val scope = UniformBlockScope()
            scope.block()
            return UniformBlock(scope.entries.toMap())
        }
    }
}

class UniformBlockScope {
    val entries = mutableMapOf<String, UniformValue>()
    fun float1(name: String, v: Float) { entries[name] = UniformValue.F1(v) }
    fun float2(name: String, x: Float, y: Float) { entries[name] = UniformValue.F2(x, y) }
    fun float3(name: String, x: Float, y: Float, z: Float) { entries[name] = UniformValue.F3(x, y, z) }
    fun float4(name: String, x: Float, y: Float, z: Float, w: Float) { entries[name] = UniformValue.F4(x, y, z, w) }
    fun int1(name: String, v: Int) { entries[name] = UniformValue.I1(v) }
    fun mat3x3(name: String, m: Matrix33) { entries[name] = UniformValue.M3(m) }
    fun mat4x4(name: String, values: FloatArray) { entries[name] = UniformValue.M4(values) }
}

sealed interface UniformValue {
    data class F1(val v: Float) : UniformValue
    data class F2(val x: Float, val y: Float) : UniformValue
    data class F3(val x: Float, val y: Float, val z: Float) : UniformValue
    data class F4(val x: Float, val y: Float, val z: Float, val w: Float) : UniformValue
    data class I1(val v: Int) : UniformValue
    data class M3(val m: Matrix33) : UniformValue
    /**
     * Immutable matrix value preserving the former `copy` and destructuring surface.
     *
     * This is deliberately not a Kotlin data class: exposing data-class metadata would also
     * expose the mutable array held by its generated component/copy machinery.
     */
    class M4(values: FloatArray) : UniformValue {
        private val snapshot = values.copyOf()
        val values: FloatArray
            get() = snapshot.copyOf()

        /** Preserves the former data-class destructuring API without exposing mutable storage. */
        operator fun component1(): FloatArray = values

        /** Preserves the former data-class copy API while snapshotting the supplied array. */
        fun copy(values: FloatArray = snapshot): M4 = M4(values)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is M4) return false
            return snapshot.contentEquals(other.snapshot)
        }

        override fun hashCode(): Int = snapshot.contentHashCode()

        override fun toString(): String = "M4(values=${snapshot.contentToString()})"
    }
}

private fun UniformValue.snapshotForUniformBlock(): UniformValue = when (this) {
    is UniformValue.F1 -> copy()
    is UniformValue.F2 -> copy()
    is UniformValue.F3 -> copy()
    is UniformValue.F4 -> copy()
    is UniformValue.I1 -> copy()
    is UniformValue.M3 -> copy()
    is UniformValue.M4 -> UniformValue.M4(values)
}
