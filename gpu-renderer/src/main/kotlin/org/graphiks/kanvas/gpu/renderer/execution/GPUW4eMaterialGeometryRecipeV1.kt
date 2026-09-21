package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.PathDrawGeometry
import org.graphiks.kanvas.gpu.plan.PathRenderPhase
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.recording.*

/** Pure W4e recipes shared by host sealing and native realization. */
internal enum class GPUW4eMaterialGeometryRecipeV1 { Consumer, BinaryConsumer, MaskedPath, UnmaskedCover, UnmaskedPath }

internal fun sealW4eMaterialGeometryHostV1(packet: GPUDrawPacket,
    commonFinalSource: Boolean = packet.w5bFinalFrameWitnessV3?.w4eLane != null): GPUW5aGeometryHostTemplateV1? {
    if (packet.w4ePreparedFrameAuthority == null) return null
    val path = packet.w4ePreparedPath ?: return null
    val consumer = packet.w4ePreparedClipConsumer
    val mask = consumer is GPUW4ePreparedClipConsumerAuthority.Mask || consumer is GPUW4ePreparedClipConsumerAuthority.InverseMask
    val stencilCover = path.phase in setOf(PathRenderPhase.SingleSampleStencilColorCover,
        PathRenderPhase.MultisampleStencilColorCover, PathRenderPhase.HardEdgeMaskStencilCover)
    val geometry = path.copyGeometry()
    val direct = when (geometry) {
        is PathDrawGeometry.Fill -> geometry.valueF32.copyDirectTriangleF32OrNull() != null
        is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32().copyDirectTriangleF32OrNull() != null
        else -> false
    }
    val recipe = when {
        stencilCover -> if (mask) GPUW4eMaterialGeometryRecipeV1.Consumer else GPUW4eMaterialGeometryRecipeV1.UnmaskedCover
        consumer is GPUW4ePreparedClipConsumerAuthority.InverseDomain ->
            if (consumer.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Zero && direct)
                GPUW4eMaterialGeometryRecipeV1.UnmaskedPath else GPUW4eMaterialGeometryRecipeV1.UnmaskedCover
        path.phase == PathRenderPhase.HardEdgeBinaryColorCover ->
            if (mask) GPUW4eMaterialGeometryRecipeV1.BinaryConsumer else GPUW4eMaterialGeometryRecipeV1.UnmaskedCover
        direct -> if (mask) GPUW4eMaterialGeometryRecipeV1.MaskedPath else GPUW4eMaterialGeometryRecipeV1.UnmaskedPath
        mask -> GPUW4eMaterialGeometryRecipeV1.Consumer
        else -> GPUW4eMaterialGeometryRecipeV1.UnmaskedCover
    }
    val unmasked = recipe == GPUW4eMaterialGeometryRecipeV1.UnmaskedCover || recipe == GPUW4eMaterialGeometryRecipeV1.UnmaskedPath
    val finalBlend = packet.blendPlan.takeIf { commonFinalSource &&
        recipe != GPUW4eMaterialGeometryRecipeV1.BinaryConsumer }
    return GPUW5aGeometryHostTemplateV1(packet.packetId.value, recipe.name, w4eMaterialGeometrySourceV1(recipe),
        "vs_main", "fs_main", ColorTargetState(GPUTextureFormat.RGBA8UnormSrgb, w4eFinalBlendState(finalBlend)).hostTargetV1(),
        w4eMaterialGeometryLayoutV1(recipe).hostLayoutV1(),
        if (unmasked) GPUW5bInlineCoverageV3.NativeFull else if (recipe == GPUW4eMaterialGeometryRecipeV1.BinaryConsumer) null
            else GPUW5bInlineCoverageV3.NativeMask,
        if (unmasked) MaterialCoordinateSlotV1.FragmentPosition else MaterialCoordinateSlotV1.Position)
}

internal fun w4eMaterialGeometrySourceV1(recipe: GPUW4eMaterialGeometryRecipeV1): String = when (recipe) {
    GPUW4eMaterialGeometryRecipeV1.Consumer -> w4eFullscreenVertexShader() + """
            struct ConsumerBlock { color: vec4f, inverse: f32, padding0: f32, padding1: f32, padding2: f32 };
            @group(0) @binding(0) var clipMask: texture_2d<f32>;
            @group(0) @binding(1) var<uniform> consumer: ConsumerBlock;
            @fragment fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
                let maskSample: vec4f = textureLoad(clipMask, vec2i(position.xy), 0);
                let rawCoverage = clamp(maskSample.r, 0.0, 1.0);
                let coverage = select(rawCoverage, 1.0 - rawCoverage, consumer.inverse > 0.5);
                return consumer.color * coverage;
            }
        """.trimIndent()
    GPUW4eMaterialGeometryRecipeV1.BinaryConsumer -> w4eFullscreenVertexShader() + """
            struct ConsumerBlock { color: vec4f, inverse: f32, padding0: f32, padding1: f32, padding2: f32 };
            @group(0) @binding(0) var pathMask: texture_2d<f32>;
            @group(0) @binding(1) var clipMask: texture_2d<f32>;
            @group(0) @binding(2) var<uniform> consumer: ConsumerBlock;
            @fragment fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
                let coordinate = vec2i(position.xy);
                let pathSample: vec4f = textureLoad(pathMask, coordinate, 0);
                let clipSample: vec4f = textureLoad(clipMask, coordinate, 0);
                let pathCoverage = clamp(pathSample.r, 0.0, 1.0);
                let rawClip = clamp(clipSample.r, 0.0, 1.0);
                let clipCoverage = select(rawClip, 1.0 - rawClip, consumer.inverse > 0.5);
                return consumer.color * (pathCoverage * clipCoverage);
            }
        """.trimIndent()
    GPUW4eMaterialGeometryRecipeV1.MaskedPath -> """
            struct ConsumerBlock { color: vec4f, inverse: f32, padding0: f32, padding1: f32, padding2: f32 };
            @group(0) @binding(0) var clipMask: texture_2d<f32>;
            @group(0) @binding(1) var<uniform> consumer: ConsumerBlock;
            @vertex fn vs_main(@location(0) position: vec2f) -> @builtin(position) vec4f {
                return vec4f(position, 0.0, 1.0);
            }
            @fragment fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
                let maskSample: vec4f = textureLoad(clipMask, vec2i(position.xy), 0);
                let rawCoverage = clamp(maskSample.r, 0.0, 1.0);
                let coverage = select(rawCoverage, 1.0 - rawCoverage, consumer.inverse > 0.5);
                return consumer.color * coverage;
            }
        """.trimIndent()
    GPUW4eMaterialGeometryRecipeV1.UnmaskedCover -> w4eFullscreenVertexShader() + """
            struct ColorBlock { color: vec4f };
            @group(0) @binding(0) var<uniform> consumer: ColorBlock;
            @fragment fn fs_main() -> @location(0) vec4f { return consumer.color; }
        """.trimIndent()
    GPUW4eMaterialGeometryRecipeV1.UnmaskedPath -> """
            struct ColorBlock { color: vec4f };
            @group(0) @binding(0) var<uniform> consumer: ColorBlock;
            @vertex fn vs_main(@location(0) position: vec2f) -> @builtin(position) vec4f {
                return vec4f(position, 0.0, 1.0);
            }
            @fragment fn fs_main() -> @location(0) vec4f { return consumer.color; }
        """.trimIndent()
}

internal fun w4eMaterialGeometryLayoutV1(recipe: GPUW4eMaterialGeometryRecipeV1): BindGroupLayoutDescriptor = when (recipe) {
    GPUW4eMaterialGeometryRecipeV1.Consumer -> BindGroupLayoutDescriptor(
        label = "Kanvas.frame.w4e.consumerLayout",
        entries = listOf(
            BindGroupLayoutEntry(binding = 0u, visibility = GPUShaderStage.Fragment, texture = TextureBindingLayout(
                sampleType = GPUTextureSampleType.Float, viewDimension = GPUTextureViewDimension.TwoD, multisampled = false,
            )),
            BindGroupLayoutEntry(binding = 1u, visibility = GPUShaderStage.Fragment, buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,minBindingSize=32uL)),
        ),
    )
    GPUW4eMaterialGeometryRecipeV1.BinaryConsumer -> BindGroupLayoutDescriptor(
        label = "Kanvas.frame.w4e.binaryConsumerLayout",
        entries = listOf(0u, 1u).map { binding -> BindGroupLayoutEntry(
            binding = binding,
            visibility = GPUShaderStage.Fragment,
            texture = TextureBindingLayout(
                sampleType = GPUTextureSampleType.Float,
                viewDimension = GPUTextureViewDimension.TwoD,
                multisampled = false,
            ),
        ) } + BindGroupLayoutEntry(
            binding = 2u,
            visibility = GPUShaderStage.Fragment,
            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,minBindingSize=32uL),
        ),
    )
    GPUW4eMaterialGeometryRecipeV1.MaskedPath -> BindGroupLayoutDescriptor(
        label = "Kanvas.frame.w4e.maskedPathLayout",
        entries = listOf(
            BindGroupLayoutEntry(binding = 0u, visibility = GPUShaderStage.Fragment, texture = TextureBindingLayout(
                sampleType = GPUTextureSampleType.Float, viewDimension = GPUTextureViewDimension.TwoD, multisampled = false,
            )),
            BindGroupLayoutEntry(binding = 1u, visibility = GPUShaderStage.Fragment,
                buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,minBindingSize=32uL)),
        ),
    )
    GPUW4eMaterialGeometryRecipeV1.UnmaskedCover -> BindGroupLayoutDescriptor(
        label = "Kanvas.frame.w4e.unmaskedPathLayout",
        entries = listOf(BindGroupLayoutEntry(
            binding = 0u,
            visibility = GPUShaderStage.Fragment,
            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,minBindingSize=16uL),
        )),
    )
    GPUW4eMaterialGeometryRecipeV1.UnmaskedPath -> BindGroupLayoutDescriptor(
        label = "Kanvas.frame.w4e.unmaskedDirectPathLayout",
        entries = listOf(BindGroupLayoutEntry(
            binding = 0u,
            visibility = GPUShaderStage.Fragment,
            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform,minBindingSize=16uL),
        )),
    )
}
