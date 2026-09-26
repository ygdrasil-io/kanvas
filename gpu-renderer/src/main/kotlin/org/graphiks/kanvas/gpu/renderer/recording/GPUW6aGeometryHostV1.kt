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

/** Mechanically lowers the planner-owned recipe; it deliberately does not inspect the draw packet's authority. */
internal fun w6aGeometryTemplate(packet: GPUDrawPacket, recipe: W6SolidRectHostRecipeV1): GPUW5aGeometryHostTemplateV1 {
    require(recipe.family == W6SolidRectGeometryFamilyV1.FullscreenTriangle)
    require(recipe.target == W6SolidRectTargetV1.Rgba8UnormSrgbSingleSample)
    require(recipe.coordinateSlot == W6SolidRectCoordinateSlotV1.FragmentPosition)
    val frozen = (recipe.colorMode as? W6SolidRectColorModeV1.FrozenColor)?.color?.copyColorF32()
    val source = frozen?.let(::w6aFrozenColorShader) ?: W6A_RECT_SHADER
    val layout = when (recipe.groupZeroAbi) {
        W6SolidRectGroupZeroAbiV1.UniformColor16 -> {
            require(recipe.colorMode is W6SolidRectColorModeV1.UniformColor16)
            GPUW5aHostBindGroupLayoutV1.of(listOf(GPUW5aHostBindGroupEntryV1(0, 2u,
                GPUW5aHostBindingLayoutV1.Buffer(GPUBufferBindingType.Uniform, false, 16L))))
        }
        W6SolidRectGroupZeroAbiV1.Empty -> {
            require(frozen != null)
            GPUW5aHostBindGroupLayoutV1.of(emptyList())
        }
    }
    val origin = recipe.materialOriginDeviceI32
    val colorKey = frozen?.let { ".${it.red.toBits()}.${it.green.toBits()}.${it.blue.toBits()}.${it.alpha.toBits()}" }.orEmpty()
    return GPUW5aGeometryHostTemplateV1(packet.packetId.value,
        "w6a.rect.v1.${recipe.site.ownerPassId.value}.${recipe.site.drawOrdinalI32}.${origin.x}.${origin.y}$colorKey", source, "vs_main", "fs_main",
        w6aColorTarget(recipe.blend).hostTargetV1(), layout, null, MaterialCoordinateSlotV1.FragmentPosition,
        materialDevicePointWgsl = "fragment_position.xy + vec2<f32>(${origin.x}.0, ${origin.y}.0)")
}

/** A legacy colour is already sealed into the picture stream and has no W5 uniform row. */
private fun w6aFrozenColorShader(color: org.graphiks.math.color.ColorF32): String {
    fun component(value: Float): String {
        require(value.isFinite()) { "Frozen W6 colour must be finite" }
        return if (value == 0f) "0.0" else value.toString().replace('E', 'e')
    }
    return W6A_VERTEX_SHADER + """
        @fragment fn fs_main(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
            return vec4<f32>(${component(color.red)}, ${component(color.green)}, ${component(color.blue)}, ${component(color.alpha)});
        }
    """
}

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
    if (blend is BlendPlan.DestinationReadV1) return ColorTargetState(GPUTextureFormat.RGBA8UnormSrgb,
        BlendState(BlendComponent(GPUBlendOperation.Add, GPUBlendFactor.One, GPUBlendFactor.Zero),
            BlendComponent(GPUBlendOperation.Add, GPUBlendFactor.One, GPUBlendFactor.Zero)))
    val fixed = blend as? BlendPlan.FixedFunctionV1
    val noOp = blend == BlendPlan.NoOpV1
    require(fixed != null || blend == BlendPlan.LegacySrcOverV1 || noOp)
    return ColorTargetState(GPUTextureFormat.RGBA8UnormSrgb, BlendState(
        BlendComponent(GPUBlendOperation.Add,
            if (noOp) GPUBlendFactor.Zero else fixed?.colorSource?.let(::factor) ?: GPUBlendFactor.One,
            if (noOp) GPUBlendFactor.One else fixed?.colorDestination?.let(::factor) ?: GPUBlendFactor.OneMinusSrcAlpha),
        BlendComponent(GPUBlendOperation.Add,
            if (noOp) GPUBlendFactor.Zero else fixed?.alphaSource?.let(::factor) ?: GPUBlendFactor.One,
            if (noOp) GPUBlendFactor.One else fixed?.alphaDestination?.let(::factor) ?: GPUBlendFactor.OneMinusSrcAlpha)))
}
