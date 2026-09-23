package org.graphiks.kanvas.render.ir

import java.security.MessageDigest

/** Checked streaming canonical preimage. No platform enum or backend value enters the wire. */
public class CanonicalHashBytesV1(domain: String) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var sizeBytesI64 = 0L
    init {
        require(domain.isNotEmpty() && domain.all { it.code in 1..127 })
        append(domain.toByteArray(Charsets.US_ASCII))
        u8(0)
    }
    private fun append(bytes: ByteArray) {
        sizeBytesI64 = Math.addExact(sizeBytesI64, bytes.size.toLong())
        require(sizeBytesI64 <= 0xffff_ffffL) { "Canonical hash byte count exceeds U32" }
        digest.update(bytes)
    }
    public fun u8(valueI32: Int): CanonicalHashBytesV1 = apply {
        require(valueI32 in 0..255)
        append(byteArrayOf(valueI32.toByte()))
    }
    public fun u32(valueI64: Long): CanonicalHashBytesV1 = apply {
        require(valueI64 in 0L..0xffff_ffffL)
        i32(valueI64.toInt())
    }
    public fun i32(valueI32: Int): CanonicalHashBytesV1 = apply {
        append(ByteArray(4) { (valueI32 ushr (it * 8)).toByte() })
    }
    public fun i64(valueI64: Long): CanonicalHashBytesV1 = apply {
        append(ByteArray(8) { (valueI64 ushr (it * 8)).toByte() })
    }
    public fun text(value: String): CanonicalHashBytesV1 = apply {
        var countI64 = 0L
        var indexI32 = 0
        while (indexI32 < value.length) {
            val c = value[indexI32++]
            val sizeI64 = when {
                c.code < 0x80 -> 1L
                c.code < 0x800 -> 2L
                c.isHighSurrogate() -> {
                    require(indexI32 < value.length && value[indexI32++].isLowSurrogate()) { "Invalid UTF-8 text" }
                    4L
                }
                else -> { require(!c.isLowSurrogate()) { "Invalid UTF-8 text" }; 3L }
            }
            countI64 = Math.addExact(countI64, sizeI64)
        }
        require(countI64 <= Int.MAX_VALUE.toLong())
        require(Math.addExact(Math.addExact(sizeBytesI64, 4L), countI64) <= 0xffff_ffffL)
        u32(countI64)
        append(value.toByteArray(Charsets.UTF_8))
    }
    public fun <T : Any> option(value: T?, encode: CanonicalHashBytesV1.(T) -> Unit): CanonicalHashBytesV1 = apply {
        u8(if (value == null) 0 else 1)
        if (value != null) encode(value)
    }
    public fun listCount(countI64: Long): CanonicalHashBytesV1 = u32(countI64)
    public fun <T> list(values: Collection<T>, encode: CanonicalHashBytesV1.(T) -> Unit): CanonicalHashBytesV1 = apply {
        listCount(values.size.toLong())
        values.forEach { encode(it) }
    }
    public fun sha256Hex(): String = (digest.clone() as MessageDigest).digest().joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }
}

internal fun RuntimeUniformType.tagU32(): Long = when (this) {
    RuntimeUniformType.FLOAT -> 1L
    RuntimeUniformType.INT1 -> 2L
    RuntimeUniformType.FLOAT2 -> 3L
    RuntimeUniformType.FLOAT3 -> 4L
    RuntimeUniformType.FLOAT4 -> 5L
    RuntimeUniformType.MAT3X3 -> 6L
    RuntimeUniformType.MAT4X4 -> 7L
}
internal fun RuntimeChildType.tagU32(legacy: Boolean): Long = when (this) {
    RuntimeChildType.SHADER -> 1L
    RuntimeChildType.COLOR_FILTER -> 2L
    RuntimeChildType.IMAGE_FILTER -> if (legacy) 3L else 4L
    RuntimeChildType.BLENDER -> if (legacy) 4L else 3L
}
internal fun RuntimeEffectAbi.tagU32(legacy: Boolean): Long = when (this) {
    RuntimeEffectAbi.SHADER -> 1L
    RuntimeEffectAbi.COLOR_FILTER -> 2L
    RuntimeEffectAbi.IMAGE_FILTER -> if (legacy) 3L else 4L
    RuntimeEffectAbi.BLENDER -> if (legacy) 4L else 3L
}
internal fun RuntimeSamplerTypeV1.tagU32(): Long = when (this) {
    RuntimeSamplerTypeV1.FILTERING -> 1L
    RuntimeSamplerTypeV1.NON_FILTERING -> 2L
}
internal fun CanonicalHashBytesV1.legacyUniform(slot: RuntimeUniformSlot) {
    text(slot.name).i32(slot.binding).u32(slot.type.tagU32()).i32(slot.size)
}
internal fun legacyRuntimeAbiHash(id: RuntimeEffectId, abi: RuntimeEffectAbi, legacy: RuntimeEffectLegacyV0): String {
    val bytes = CanonicalHashBytesV1("kanvas-runtime-effect-legacy-abi-v0")
    bytes.text(id.value).u32(abi.tagU32(true))
        .list(legacy.uniformLayout.toList()) { legacyUniform(it) }
        .list(legacy.childSlots) { text(it.name).u32(it.type.tagU32(true)) }
        .option(legacy.vertexLayout) { layout ->
            i32(layout.stride).u32(when (layout.stepMode) { RuntimeVertexStepMode.VERTEX -> 1L; RuntimeVertexStepMode.INSTANCE -> 2L })
            list(layout.toList()) { attribute ->
                u32(when (attribute.format) {
                    RuntimeVertexFormat.FLOAT32 -> 1L
                    RuntimeVertexFormat.FLOAT32X2 -> 2L
                    RuntimeVertexFormat.FLOAT32X3 -> 3L
                    RuntimeVertexFormat.FLOAT32X4 -> 4L
                    RuntimeVertexFormat.UINT8X4 -> 5L
                    RuntimeVertexFormat.SINT16X2 -> 6L
                    RuntimeVertexFormat.SINT16X4 -> 7L
                }).i32(attribute.offset).i32(attribute.shaderLocation)
            }
        }.option(legacy.module) { module ->
            text(module.source).text(module.entryPoint)
            list(module.uniforms()) { legacyUniform(it) }
            list(module.textures()) { text(it.name).i32(it.binding) }
        }
    return bytes.sha256Hex()
}

internal fun positiveRuntimeAbiHash(
    id: RuntimeEffectId, abi: RuntimeEffectAbi, versionI32: Int, block: RuntimeUniformBlockV1,
    children: List<RuntimeChildSlotV2>, resources: List<RuntimeLogicalResourceSlotV1>,
): String = CanonicalHashBytesV1("kanvas-runtime-effect-abi-v1").apply {
    // W5h uses the logical ID as the parent ABI's entrypointName, never a WGSL entrypoint.
    u32(abi.tagU32(false)).i32(versionI32).text(id.value).u32(1).u32(1).text("WgslFloatEnvelopeV1")
    list(children) { text(it.name).u32(it.type.tagU32(false)).u8(if (it.nullable) 1 else 0) }
    i32(block.sizeBytesI32)
    list(block.slots) { text(it.name).u32(it.type.tagU32()).i32(it.offsetBytesI32).i32(it.sizeBytesI32)
        .i32(it.alignmentBytesI32).i32(it.arrayCountI32).i32(it.arrayStrideBytesI32) }
    list(resources) { slot ->
        text(slot.name).i32(slot.logicalSlotI32).u32(when (slot.kind) {
            RuntimeLogicalResourceKindV1.STORAGE_BUFFER -> 1L
            RuntimeLogicalResourceKindV1.SAMPLED_TEXTURE -> 2L
            RuntimeLogicalResourceKindV1.SAMPLER -> 3L
        })
        val storage = slot.facts as? RuntimeLogicalResourceFactsV1.StorageRead
        val texture = slot.facts as? RuntimeLogicalResourceFactsV1.Texture2DFloatFilterable
        val sampler = slot.facts as? RuntimeLogicalResourceFactsV1.Sampler
        option(storage) { u32(2) }.option(storage) { u32(1) }
        option(storage) { i64(it.minBindingSizeBytesI64) }
        option(texture) { u32(1) }.option(texture) { u32(1) }
        option(texture) { u8(if (it.multisampled) 1 else 0) }
        option(sampler) { u32(it.type.tagU32()) }
    }
}.sha256Hex()
