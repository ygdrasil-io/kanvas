package org.graphiks.kanvas.gpu.renderer.recording

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.execution.MaterialCoordinateSlotV1
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket

internal const val W6A_VERTEX_SHADER: String = """
    @vertex fn vs_main(@builtin(vertex_index) index: u32) -> @builtin(position) vec4<f32> {
        let positions = array<vec2<f32>, 3>(vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0));
        return vec4<f32>(positions[index], 0.0, 1.0);
    }
"""
internal const val W6A_RECT_SHADER: String = W6A_VERTEX_SHADER + """
    struct Core { premul_rgba: vec4<f32>, }
    @group(0) @binding(0) var<uniform> core: Core;
    @fragment fn fs_main(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
        return core.premul_rgba;
    }
"""

internal fun w6aGeometryTemplate(packet: GPUDrawPacket, blend: BlendPlan): GPUW5aGeometryHostTemplateV1 =
    GPUW5aGeometryHostTemplateV1(packet.packetId.value, "w6a.rect.v1.${packet.commandIdValue}", W6A_RECT_SHADER, "vs_main", "fs_main",
        w6aColorTarget(blend).hostTargetV1(), GPUW5aHostBindGroupLayoutV1.of(listOf(GPUW5aHostBindGroupEntryV1(0, 2u,
            GPUW5aHostBindingLayoutV1.Buffer(GPUBufferBindingType.Uniform, false, 16L)))), null, MaterialCoordinateSlotV1.FragmentPosition)

internal fun w6aColorTarget(blend: BlendPlan): ColorTargetState {
    fun factor(value: BlendFactorV1): GPUBlendFactor = when (value) {
        BlendFactorV1.Zero -> GPUBlendFactor.Zero
        BlendFactorV1.One -> GPUBlendFactor.One
        BlendFactorV1.SrcAlpha -> GPUBlendFactor.SrcAlpha
        BlendFactorV1.OneMinusSrcAlpha -> GPUBlendFactor.OneMinusSrcAlpha
        BlendFactorV1.DstAlpha -> GPUBlendFactor.DstAlpha
        BlendFactorV1.OneMinusDstAlpha -> GPUBlendFactor.OneMinusDstAlpha
        BlendFactorV1.SrcColor -> GPUBlendFactor.Src
        BlendFactorV1.OneMinusSrcColor -> GPUBlendFactor.OneMinusSrc
    }
    val fixed = blend as? BlendPlan.FixedFunctionV1
    require(fixed != null || blend == BlendPlan.LegacySrcOverV1)
    return ColorTargetState(GPUTextureFormat.RGBA8UnormSrgb, BlendState(
        BlendComponent(GPUBlendOperation.Add, fixed?.colorSource?.let(::factor) ?: GPUBlendFactor.One, fixed?.colorDestination?.let(::factor) ?: GPUBlendFactor.OneMinusSrcAlpha),
        BlendComponent(GPUBlendOperation.Add, fixed?.alphaSource?.let(::factor) ?: GPUBlendFactor.One, fixed?.alphaDestination?.let(::factor) ?: GPUBlendFactor.OneMinusSrcAlpha)))
}
