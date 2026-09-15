package org.graphiks.kanvas.gpu.renderer.recording

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.renderer.execution.*
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket

/** Immutable geometry recipe, sealed before any native ownership exists. */
internal data class GPUW5aGeometryHostTemplateV1(
    val packetId: String,
    val pipelineRecipeId: String,
    val sourceWgsl: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val target: GPUW5aHostColorTargetV1,
    val groupZeroLayout: GPUW5aHostBindGroupLayoutV1,
    val w5bInlineCoverageV3: GPUW5bInlineCoverageV3?,
    val materialCoordinateSlot: MaterialCoordinateSlotV1?,
)

internal data class GPUW5aHostColorTargetV1(
    val format: GPUTextureFormat,
    val colorBlend: GPUW5aHostBlendComponentV1,
    val alphaBlend: GPUW5aHostBlendComponentV1,
    val writeMaskU32: UInt,
)

internal data class GPUW5aHostBlendComponentV1(
    val operation: GPUBlendOperation,
    val srcFactor: GPUBlendFactor,
    val dstFactor: GPUBlendFactor,
)

internal sealed interface GPUW5aHostBindingLayoutV1 {
    data class Buffer(val type: GPUBufferBindingType, val hasDynamicOffset: Boolean,
        val minBindingSizeBytesI64: Long) : GPUW5aHostBindingLayoutV1
    data class Texture(val sampleType: GPUTextureSampleType, val viewDimension: GPUTextureViewDimension,
        val multisampled: Boolean) : GPUW5aHostBindingLayoutV1
    data class Sampler(val type: GPUSamplerBindingType) : GPUW5aHostBindingLayoutV1
}

internal data class GPUW5aHostBindGroupEntryV1(
    val bindingI32: Int,
    val visibilityFlagsU32: UInt,
    val layout: GPUW5aHostBindingLayoutV1,
)

internal class GPUW5aHostBindGroupLayoutV1 private constructor(entries: Collection<GPUW5aHostBindGroupEntryV1>) {
    val entries: List<GPUW5aHostBindGroupEntryV1> = java.util.Collections.unmodifiableList(ArrayList(entries))
    init {
        require(this.entries.map { it.bindingI32 } == this.entries.map { it.bindingI32 }.distinct().sorted())
        require(this.entries.all { it.bindingI32 >= 0 && it.visibilityFlagsU32 in 1u..7u })
    }
    companion object {
        fun of(entries: Collection<GPUW5aHostBindGroupEntryV1>): GPUW5aHostBindGroupLayoutV1 = GPUW5aHostBindGroupLayoutV1(entries)
    }
}

internal fun BindGroupLayoutDescriptor.hostLayoutV1(): GPUW5aHostBindGroupLayoutV1 =
    GPUW5aHostBindGroupLayoutV1.of(entries.map { entry ->
        require(listOfNotNull(entry.buffer, entry.texture, entry.sampler, entry.storageTexture).size == 1)
        val layout = entry.buffer?.let {
            GPUW5aHostBindingLayoutV1.Buffer(it.type, it.hasDynamicOffset, it.minBindingSize.toLong())
        } ?: entry.texture?.let {
            GPUW5aHostBindingLayoutV1.Texture(it.sampleType, it.viewDimension, it.multisampled)
        } ?: entry.sampler?.let { GPUW5aHostBindingLayoutV1.Sampler(it.type) }
            ?: error("Unsupported material geometry layout")
        GPUW5aHostBindGroupEntryV1(entry.binding.toInt(), entry.visibility.value.toUInt(), layout)
    })

internal fun GPUW5aHostBindGroupLayoutV1.nativeDescriptorV1(label: String): BindGroupLayoutDescriptor =
    BindGroupLayoutDescriptor(label = label, entries = entries.map { entry ->
        val visibility = GPUShaderStage.entries.filter { it.value.toUInt() and entry.visibilityFlagsU32 != 0u }
            .fold(GPUShaderStage.None) { mask, bit -> mask or bit }
        when (val layout = entry.layout) {
            is GPUW5aHostBindingLayoutV1.Buffer -> BindGroupLayoutEntry(entry.bindingI32.toUInt(), visibility,
                buffer = BufferBindingLayout(layout.type, layout.hasDynamicOffset, layout.minBindingSizeBytesI64.toULong()))
            is GPUW5aHostBindingLayoutV1.Texture -> BindGroupLayoutEntry(entry.bindingI32.toUInt(), visibility,
                texture = TextureBindingLayout(layout.sampleType, layout.viewDimension, layout.multisampled))
            is GPUW5aHostBindingLayoutV1.Sampler -> BindGroupLayoutEntry(entry.bindingI32.toUInt(), visibility,
                sampler = SamplerBindingLayout(layout.type))
        }
    })

internal fun GPUColorTargetState.hostTargetV1(): GPUW5aHostColorTargetV1 {
    val blend = requireNotNull(blend)
    return GPUW5aHostColorTargetV1(format,
        GPUW5aHostBlendComponentV1(blend.color.operation, blend.color.srcFactor, blend.color.dstFactor),
        GPUW5aHostBlendComponentV1(blend.alpha.operation, blend.alpha.srcFactor, blend.alpha.dstFactor), writeMask.value.toUInt())
}

internal fun sealW5aGeometryHostTemplateV1(packet: GPUDrawPacket): GPUW5aGeometryHostTemplateV1? {
    val key = packet.corePrimitivePreparedAuthority?.structuralPipelineKey ?: return sealW4eMaterialGeometryHostV1(packet)
    val mapped = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(key) as? GPUWgpu4kCorePrimitivePipelineMapping.Mapped
        ?: return null
    val source = corePrimitiveMaterialGeometryWgslV1(mapped.componentIdentity) ?: return null
    return GPUW5aGeometryHostTemplateV1(packet.packetId.value, mapped.identity.toString(), source,
        "vs_main", "fs_main", corePrimitiveColorTargetStateV1(mapped.identity).hostTargetV1(),
        corePrimitiveBindGroupLayoutDescriptor(mapped.componentIdentity).hostLayoutV1(), null,
        MaterialCoordinateSlotV1.FragmentPosition)
}
