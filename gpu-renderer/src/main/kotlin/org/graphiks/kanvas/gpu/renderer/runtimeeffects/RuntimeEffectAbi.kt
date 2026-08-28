package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.util.Collections

sealed interface RuntimeEffectUniformAbiType {
    data object Scalar : RuntimeEffectUniformAbiType
    data class Vector(val lanes: Int) : RuntimeEffectUniformAbiType
    data class Matrix(val columns: Int, val rows: Int) : RuntimeEffectUniformAbiType
    data class Array(val element: RuntimeEffectUniformAbiType, val count: Int) : RuntimeEffectUniformAbiType
}

data class RuntimeEffectUniformAbiField(val name: String, val type: RuntimeEffectUniformAbiType)
data class RuntimeEffectUniformAbiMember(val offset: Int, val size: Int, val alignment: Int, val stride: Int? = null)
class RuntimeEffectUniformAbiComputedLayout(
    fields: Map<String, RuntimeEffectUniformAbiMember>,
    val byteSize: Int,
) {
    val fields: Map<String, RuntimeEffectUniformAbiMember> =
        Collections.unmodifiableMap(LinkedHashMap(fields))
}

/** Zero-initialized uniform slab; padding is never sourced from uninitialized memory. */
class RuntimeEffectUniformSlab(byteSize: Int) {
    private val storage = ByteArray(byteSize)
    val bytes: ByteArray get() = storage.copyOf()
    fun write(offset: Int, value: ByteArray) {
        require(offset >= 0 && offset + value.size <= storage.size)
        value.copyInto(storage, offset)
    }
}

class RuntimeEffectUniformAbiLayout(private val fields: List<RuntimeEffectUniformAbiField>) {
    fun compute(): RuntimeEffectUniformAbiComputedLayout {
        require(fields.map { it.name }.all { it.isNotBlank() }) { "uniform field names must not be blank" }
        require(fields.map { it.name }.distinct().size == fields.size) { "uniform field names must be unique" }
        var cursor = 0
        val result = linkedMapOf<String, RuntimeEffectUniformAbiMember>()
        fields.forEach { field ->
            val info = typeInfo(field.type)
            cursor = align(cursor, info.alignment)
            result[field.name] = RuntimeEffectUniformAbiMember(cursor, info.size, info.alignment, info.stride)
            cursor += info.size
        }
        return RuntimeEffectUniformAbiComputedLayout(result, align(cursor, 16))
    }
    private data class Info(val alignment: Int, val size: Int, val stride: Int? = null)
    private fun typeInfo(type: RuntimeEffectUniformAbiType): Info = when (type) {
        RuntimeEffectUniformAbiType.Scalar -> Info(4, 4)
        is RuntimeEffectUniformAbiType.Vector -> when (type.lanes) { 2 -> Info(8, 8); 3 -> Info(16, 12); 4 -> Info(16, 16); else -> error("vector lanes") }
        is RuntimeEffectUniformAbiType.Matrix -> { require(type.columns in 2..4 && type.rows in 2..4); val stride = 16; Info(16, stride * type.columns, stride) }
        is RuntimeEffectUniformAbiType.Array -> { require(type.count > 0); val e = typeInfo(type.element); val stride = align(e.size, 16); Info(16, stride * type.count, stride) }
    }
    private fun align(value: Int, alignment: Int) = (value + alignment - 1) / alignment * alignment
}

sealed interface RuntimeEffectAbiValidation {
    data object Accepted : RuntimeEffectAbiValidation
    data class Refused(val code: String) : RuntimeEffectAbiValidation
}
object RuntimeEffectAbiValidator {
    fun validateBinding(expectedGroup: Int, expectedBinding: Int, actualGroup: Int, actualBinding: Int): RuntimeEffectAbiValidation =
        when {
            expectedGroup < 0 || expectedBinding < 0 || actualGroup < 0 || actualBinding < 0 ->
                RuntimeEffectAbiValidation.Refused("unsupported.runtime_effect.binding_index_invalid")
            expectedGroup == actualGroup && expectedBinding == actualBinding -> RuntimeEffectAbiValidation.Accepted
            else -> RuntimeEffectAbiValidation.Refused("unsupported.runtime_effect.binding_layout_mismatch")
        }
}

fun runtimeEffectMaterialCacheKey(id: String, version: Int, uniforms: ByteArray, children: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun part(value: ByteArray) {
        digest.update(value.size.toString().toByteArray(StandardCharsets.UTF_8))
        digest.update(':'.code.toByte())
        digest.update(value)
    }
    part(id.toByteArray(StandardCharsets.UTF_8))
    part(version.toString().toByteArray(StandardCharsets.UTF_8))
    part(uniforms)
    children.forEach { part(it.toByteArray(StandardCharsets.UTF_8)) }
    return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
}
