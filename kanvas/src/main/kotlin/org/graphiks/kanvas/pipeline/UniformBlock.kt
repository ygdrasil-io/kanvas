package org.graphiks.kanvas.pipeline

import org.graphiks.kanvas.types.Matrix33

class UniformBlock private constructor(
    val entries: Map<String, UniformValue>,
) {
    companion object {
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
    data class M4(val values: FloatArray) : UniformValue {
        override fun equals(other: Any?): Boolean { if (this === other) return true; if (other !is M4) return false; return values.contentEquals(other.values) }
        override fun hashCode(): Int = values.contentHashCode()
    }
}
