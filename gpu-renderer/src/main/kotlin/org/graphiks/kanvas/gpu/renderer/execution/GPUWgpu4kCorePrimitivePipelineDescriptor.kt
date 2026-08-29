package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUFrontFace
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUStencilOperation
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePathStencilStructuralProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilStructuralProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskStructuralProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.clipStencilStructuralProgramOrNull
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveDirectPathDepthStencilState
import org.graphiks.kanvas.gpu.renderer.passes.coverageMaskStructuralProgramOrNull
import org.graphiks.kanvas.gpu.renderer.passes.pathStencilStructuralProgramOrNull
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary

/** Closed, handle-free fixed-function state consumed by native CorePrimitive pipelines. */
internal enum class GPUWgpu4kCorePrimitiveBlendProgram(
    val mode: GPUBlendMode?,
    val colorSourceFactor: String?,
    val colorDestinationFactor: String?,
    val colorOperation: String?,
    val alphaSourceFactor: String?,
    val alphaDestinationFactor: String?,
    val alphaOperation: String?,
) {
    ColorWriteNone(null, null, null, null, null, null, null),
    DestinationNoOp(GPUBlendMode.DST, null, null, null, null, null, null),
    PremulClear(GPUBlendMode.CLEAR, "zero", "zero", "add", "zero", "zero", "add"),
    PremulSrc(GPUBlendMode.SRC, "one", "zero", "add", "one", "zero", "add"),
    PremulSrcOver(
        GPUBlendMode.SRC_OVER,
        "one",
        "one-minus-src-alpha",
        "add",
        "one",
        "one-minus-src-alpha",
        "add",
    ),
    PremulDstOver(
        GPUBlendMode.DST_OVER,
        "one-minus-dst-alpha",
        "one",
        "add",
        "one-minus-dst-alpha",
        "one",
        "add",
    ),
    PremulSrcIn(GPUBlendMode.SRC_IN, "dst-alpha", "zero", "add", "dst-alpha", "zero", "add"),
    PremulDstIn(GPUBlendMode.DST_IN, "zero", "src-alpha", "add", "zero", "src-alpha", "add"),
    PremulSrcOut(
        GPUBlendMode.SRC_OUT,
        "one-minus-dst-alpha",
        "zero",
        "add",
        "one-minus-dst-alpha",
        "zero",
        "add",
    ),
    PremulDstOut(
        GPUBlendMode.DST_OUT,
        "zero",
        "one-minus-src-alpha",
        "add",
        "zero",
        "one-minus-src-alpha",
        "add",
    ),
    PremulSrcAtop(
        GPUBlendMode.SRC_ATOP,
        "dst-alpha",
        "one-minus-src-alpha",
        "add",
        "dst-alpha",
        "one-minus-src-alpha",
        "add",
    ),
    PremulDstAtop(
        GPUBlendMode.DST_ATOP,
        "one-minus-dst-alpha",
        "src-alpha",
        "add",
        "one-minus-dst-alpha",
        "src-alpha",
        "add",
    ),
    PremulXor(
        GPUBlendMode.XOR,
        "one-minus-dst-alpha",
        "one-minus-src-alpha",
        "add",
        "one-minus-dst-alpha",
        "one-minus-src-alpha",
        "add",
    ),
    PremulModulate(GPUBlendMode.MODULATE, "zero", "src", "add", "zero", "src-alpha", "add"),
    PremulScreen(
        GPUBlendMode.SCREEN,
        "one",
        "one-minus-src",
        "add",
        "one",
        "one-minus-src-alpha",
        "add",
    ),
    // Destination-read formula programs (Graphite dst-read recipe): the formula blends in the
    // shader while fixed-function state stays exact Src, so every dst-read mode lowers onto the
    // same Src state but keeps one distinct blend-program identity for the mode.
    DstReadPlus(GPUBlendMode.PLUS, "one", "zero", "add", "one", "zero", "add"),
    DstReadModulate(GPUBlendMode.MODULATE, "one", "zero", "add", "one", "zero", "add"),
    DstReadClear(GPUBlendMode.CLEAR, "one", "zero", "add", "one", "zero", "add"),
    DstReadSrc(GPUBlendMode.SRC, "one", "zero", "add", "one", "zero", "add"),
    DstReadSrcIn(GPUBlendMode.SRC_IN, "one", "zero", "add", "one", "zero", "add"),
    DstReadDstIn(GPUBlendMode.DST_IN, "one", "zero", "add", "one", "zero", "add"),
    DstReadSrcOut(GPUBlendMode.SRC_OUT, "one", "zero", "add", "one", "zero", "add"),
    DstReadDstAtop(GPUBlendMode.DST_ATOP, "one", "zero", "add", "one", "zero", "add"),
    DstReadMultiply(GPUBlendMode.MULTIPLY, "one", "zero", "add", "one", "zero", "add"),
    DstReadOverlay(GPUBlendMode.OVERLAY, "one", "zero", "add", "one", "zero", "add"),
    DstReadDarken(GPUBlendMode.DARKEN, "one", "zero", "add", "one", "zero", "add"),
    DstReadLighten(GPUBlendMode.LIGHTEN, "one", "zero", "add", "one", "zero", "add"),
    DstReadColorDodge(GPUBlendMode.COLOR_DODGE, "one", "zero", "add", "one", "zero", "add"),
    DstReadColorBurn(GPUBlendMode.COLOR_BURN, "one", "zero", "add", "one", "zero", "add"),
    DstReadHardLight(GPUBlendMode.HARD_LIGHT, "one", "zero", "add", "one", "zero", "add"),
    DstReadSoftLight(GPUBlendMode.SOFT_LIGHT, "one", "zero", "add", "one", "zero", "add"),
    DstReadDifference(GPUBlendMode.DIFFERENCE, "one", "zero", "add", "one", "zero", "add"),
    DstReadExclusion(GPUBlendMode.EXCLUSION, "one", "zero", "add", "one", "zero", "add"),
    DstReadHue(GPUBlendMode.HUE, "one", "zero", "add", "one", "zero", "add"),
    DstReadSaturation(GPUBlendMode.SATURATION, "one", "zero", "add", "one", "zero", "add"),
    DstReadColor(GPUBlendMode.COLOR, "one", "zero", "add", "one", "zero", "add"),
    DstReadLuminosity(GPUBlendMode.LUMINOSITY, "one", "zero", "add", "one", "zero", "add"),
}

internal fun GPUWgpu4kCorePrimitiveBlendProgram.isDstRead(): Boolean =
    name.startsWith("DstRead")

/** Closed native programs materialized by the bounded CorePrimitive WebGPU lane. */
internal enum class GPUWgpu4kCorePrimitivePipelineProgram {
    DirectSrcOver,
    DirectSrcOverWithPathDepthStencil,
    DirectLinearGradient,
    DirectLinearGradientRepeat,
    DirectRadialGradient,
    DirectSweepGradient,
    AnalyticShapeSrcOver,
    AnalyticShapeDstRead,
    AnalyticDRRectSrcOver,
    AnalyticLinearGradient,
    AnalyticLinearGradientRepeat,
    AnalyticRadialGradient,
    AnalyticSweepGradient,
    AnalyticClipRectHard,
    AnalyticClipRectAA,
    AnalyticClipRRectHard,
    AnalyticClipRRectAA,
    AnalyticClipIntersection4,
    PathStencilProducerWinding,
    PathStencilProducerEvenOdd,
    PathStencilCoverRegular,
    PathStencilCoverInverse,
    PathStencilCoverDstRead,
    PathStencilCoverAnalyticRectHardRegular,
    PathStencilCoverAnalyticRectHardInverse,
    PathStencilCoverAnalyticRectAARegular,
    PathStencilCoverAnalyticRectAAInverse,
    PathStencilCoverAnalyticRRectHardRegular,
    PathStencilCoverAnalyticRRectHardInverse,
    PathStencilCoverAnalyticRRectAARegular,
    PathStencilCoverAnalyticRRectAAInverse,
    ClipStencilProducerWinding,
    ClipStencilProducerEvenOdd,
    ClipStencilConsumerRegular,
    ClipStencilConsumerInverse,
    ClipStencilConsumerLinearGradientRegular,
    ClipStencilConsumerLinearGradientInverse,
    ClipStencilConsumerRadialGradientRegular,
    ClipStencilConsumerRadialGradientInverse,
    ClipStencilConsumerSweepGradientRegular,
    ClipStencilConsumerSweepGradientInverse,
    ClipStencilConsumerAnalyticRRectRegular,
    ClipStencilConsumerAnalyticRRectInverse,
    ClipStencilConsumerAnalyticDRRectRegular,
    ClipStencilConsumerAnalyticDRRectInverse,
    CoverageMaskProducerRectIntersect,
    CoverageMaskProducerRectDifference,
    CoverageMaskProducerRRectIntersect,
    CoverageMaskProducerRRectDifference,
    CoverageMaskConsumerNearest,
}

internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_INCOMPATIBLE_CLIP_REASON =
    "AnalyticShapeSrcOver accepts only NoClip or ScissorOnly; analytic, stencil, and mask clips remain closed."

internal sealed interface GPUWgpu4kCorePrimitivePipelineMapping {
    data class Mapped(
        val identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
        val componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
    ) : GPUWgpu4kCorePrimitivePipelineMapping

    data class Refused(
        val reason: String,
    ) : GPUWgpu4kCorePrimitivePipelineMapping
}

/**
 * Consumes the handle-free structural authority and accepts only a closed native program plus its
 * exact fixed-function blend program. Dynamic geometry, bounds, scissor, load/store, and stencil
 * reference never enter this identity.
 */
internal fun mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(
    structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
): GPUWgpu4kCorePrimitivePipelineMapping {
    if (structuralKey.shader in setOf(
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape,
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect,
        ) &&
        structuralKey.clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None
    ) {
        return GPUWgpu4kCorePrimitivePipelineMapping.Refused(
            CORE_PRIMITIVE_ANALYTIC_SHAPE_INCOMPATIBLE_CLIP_REASON,
        )
    }
    val program = structuralKey.nativeProgramOrNull()
        ?: return GPUWgpu4kCorePrimitivePipelineMapping.Refused(
            "CorePrimitive structural state is not one exact native program.",
        )
    val blendProgram = structuralKey.nativeBlendProgramOrNull(program)
        ?: return GPUWgpu4kCorePrimitivePipelineMapping.Refused(
            "CorePrimitive structural blend is not one exact native program.",
        )
    return GPUWgpu4kCorePrimitivePipelineMapping.Mapped(
        GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
            targetFormat = structuralKey.colorFormat.stableIdentity,
            sampleCount = structuralKey.sampleCount,
            topology = "triangle-list",
            frontFace = "ccw",
            cullMode = "none",
            program = program,
            blendProgram = blendProgram,
        ),
        componentIdentity = when {
            structuralKey.blend is
                GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination &&
                program.isAnalyticShapeDstRead() ->
                corePrimitiveAnalyticShapeDstReadComponentIdentity(
                    (structuralKey.blend as GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination)
                        .mode.gpuLabel,
                )
            structuralKey.blend is
                GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination ->
                corePrimitiveDstReadComponentIdentity(
                    (structuralKey.blend as GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination)
                        .mode.gpuLabel,
                )
            structuralKey.shader.isGradient() ->
                corePrimitiveGradientComponentIdentity(structuralKey.shader)
            program.isAnalyticShape() ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_SHAPE_COMPONENT_IDENTITY
            program.isAnalyticDRRect() -> PRODUCTION_CORE_PRIMITIVE_ANALYTIC_DRRECT_COMPONENT_IDENTITY
            program.isClipStencilProducer() ->
                PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY
            program.isCoverageMaskProducer() ->
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY
            program.isCoverageMaskConsumer() ->
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY
            program.isAnalyticIntersection4() ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY
            program.isAnalyticClip() -> PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
            else -> PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY
        },
    )
}

internal fun GPUWgpu4kCorePrimitiveComponentIdentity.gradientShaderVariantOrNull():
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader? = when (this) {
    PRODUCTION_CORE_PRIMITIVE_DIRECT_LINEAR_GRADIENT_COMPONENT_IDENTITY ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient
    PRODUCTION_CORE_PRIMITIVE_DIRECT_LINEAR_GRADIENT_REPEAT_COMPONENT_IDENTITY ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradientRepeat
    PRODUCTION_CORE_PRIMITIVE_DIRECT_RADIAL_GRADIENT_COMPONENT_IDENTITY ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectRadialGradient
    PRODUCTION_CORE_PRIMITIVE_DIRECT_SWEEP_GRADIENT_COMPONENT_IDENTITY ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectSweepGradient
    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_LINEAR_GRADIENT_COMPONENT_IDENTITY ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradient
    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_LINEAR_GRADIENT_REPEAT_COMPONENT_IDENTITY ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradientRepeat
    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_RADIAL_GRADIENT_COMPONENT_IDENTITY ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRadialGradient
    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_SWEEP_GRADIENT_COMPONENT_IDENTITY ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticSweepGradient
    else -> null
}

internal fun GPUWgpu4kCorePrimitiveComponentIdentity.gradientProgramOrNull():
    GPUWgpu4kCorePrimitivePipelineProgram? = when (gradientShaderVariantOrNull()) {
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient ->
        GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradient
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradientRepeat ->
        GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradientRepeat
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectRadialGradient ->
        GPUWgpu4kCorePrimitivePipelineProgram.DirectRadialGradient
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectSweepGradient ->
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSweepGradient
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradient ->
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradient
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradientRepeat ->
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradientRepeat
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRadialGradient ->
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticRadialGradient
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticSweepGradient ->
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticSweepGradient
    else -> null
}

/**
 * Per-key bind-group component identity (the exact bind-group layout authority) for a structural
 * key. Destination-reading shading keys project onto the per-mode dst-read component whose
 * fragment layout appends the ordered destination texture and sampler slots; every other admitted
 * key projects onto the same component the pipeline cache selects for its closed program. Refused
 * keys carry no component identity.
 */
internal fun GPUCorePrimitiveRenderPipelineStructuralKey.corePrimitiveNativeComponentIdentityOrNull():
    GPUWgpu4kCorePrimitiveComponentIdentity? {
    if (role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading &&
        blend is GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination
    ) {
        val shader = blend as GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination
        if (this.shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape) {
            if (shader.sourceCoverage == GPUSourceCoverageEncoding.LCDCoverageInShader ||
                GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
                    shader.mode.gpuLabel,
                    shader.formulaId,
                ) == null
            ) {
                return null
            }
            return corePrimitiveAnalyticShapeDstReadComponentIdentity(shader.mode.gpuLabel)
        }
        if (shader.sourceCoverage != GPUSourceCoverageEncoding.None ||
            GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
                shader.mode.gpuLabel,
                shader.formulaId,
            ) == null
        ) {
            return null
        }
        return corePrimitiveDstReadComponentIdentity(shader.mode.gpuLabel)
    }
    return when (
        val mapped = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(this)
    ) {
        is GPUWgpu4kCorePrimitivePipelineMapping.Mapped -> when {
            mapped.identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverDstRead ->
                mapped.componentIdentity
            this.shader.isGradient() ->
                corePrimitiveGradientComponentIdentity(this.shader)
            mapped.identity.program.isAnalyticShape() ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_SHAPE_COMPONENT_IDENTITY
            mapped.identity.program.isAnalyticDRRect() ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_DRRECT_COMPONENT_IDENTITY
            mapped.identity.program.isClipStencilProducer() ->
                PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY
            mapped.identity.program.isCoverageMaskProducer() ->
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY
            mapped.identity.program.isCoverageMaskConsumer() ->
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_COMPONENT_IDENTITY
            mapped.identity.program.isAnalyticIntersection4() ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY
            mapped.identity.program.isAnalyticClip() ->
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
            else -> PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY
        }
        is GPUWgpu4kCorePrimitivePipelineMapping.Refused -> null
    }
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.nativeProgramOrNull():
    GPUWgpu4kCorePrimitivePipelineProgram? {
    if (sampleCount !in setOf(1, 4) ||
        frontFace != GPUCorePrimitiveRenderPipelineStructuralKey.FrontFace.Ccw ||
        cullMode != GPUCorePrimitiveRenderPipelineStructuralKey.CullMode.None ||
        colorFormat !in GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.entries
    ) return null
    if (sampleCount == 4 && !supportsFourSampleProgram()) return null

    return when (role) {
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading -> when (shader) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape -> when {
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
                    clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ||
                    depthStencil != GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None -> null
                blend is GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination -> {
                    val shader =
                        blend as GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination
                    if (shader.sourceCoverage == GPUSourceCoverageEncoding.LCDCoverageInShader ||
                        GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
                            shader.mode.gpuLabel,
                            shader.formulaId,
                        ) == null
                    ) {
                        null
                    } else {
                        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeDstRead
                    }
                }
                blend.nativeShadingBlendProgramOrNull() == null -> null
                else -> GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeSrcOver
            }
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect -> when {
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
                    clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ||
                    depthStencil != GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ||
                    (blend as? GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed)?.mode !=
                        GPUBlendMode.SRC_OVER ||
                    blend.nativeShadingBlendProgramOrNull() !=
                        GPUWgpu4kCorePrimitiveBlendProgram.PremulSrcOver -> null
                else -> GPUWgpu4kCorePrimitivePipelineProgram.AnalyticDRRectSrcOver
            }
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient ->
                gradientProgramOrNull(GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradient)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradientRepeat ->
                gradientProgramOrNull(GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradientRepeat)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectRadialGradient ->
                gradientProgramOrNull(GPUWgpu4kCorePrimitivePipelineProgram.DirectRadialGradient)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectSweepGradient ->
                gradientProgramOrNull(GPUWgpu4kCorePrimitivePipelineProgram.DirectSweepGradient)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradient ->
                gradientProgramOrNull(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradient)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradientRepeat ->
                gradientProgramOrNull(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradientRepeat)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRadialGradient ->
                gradientProgramOrNull(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticRadialGradient)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticSweepGradient ->
                gradientProgramOrNull(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticSweepGradient)
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry -> when {
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
                    blend.nativeShadingBlendProgramOrNull() == null -> null
                sampleCount == 4 && clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None -> null
                sampleCount == 4 &&
                    depthStencil != GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None &&
                    depthStencil != corePrimitiveDirectPathDepthStencilState() -> null
                clip is GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic &&
                    depthStencil == GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ->
                    clip.nativeAnalyticProgramOrNull()
                clip == GPUCorePrimitiveRenderPipelineStructuralKey.Clip.AnalyticIntersection4 &&
                    depthStencil == GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ->
                    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipIntersection4
                clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None -> null
                depthStencil == GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ->
                    GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver
                depthStencil == corePrimitiveDirectPathDepthStencilState() ->
                    GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil
                else -> null
            }
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when {
            clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None -> null
            shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan &&
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StrokeStencilEdgeFan ||
                blend != GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone -> null
            depthStencil.pathStencilStructuralProgramOrNull() ==
                GPUCorePrimitivePathStencilStructuralProgram.ProducerWinding ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding
            depthStencil.pathStencilStructuralProgramOrNull() ==
                GPUCorePrimitivePathStencilStructuralProgram.ProducerEvenOdd ->
                GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover -> when {
            shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList -> null
            blend is GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination -> {
                // The continued cover shades the path with a destination-read
                // formula; the fan already supplies full coverage via the stencil test, so the
                // source coverage must be None (full) and only NoClip/ScissorOnly is closed.
                val shaderBlend =
                    blend as GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination
                if (shaderBlend.sourceCoverage != GPUSourceCoverageEncoding.None ||
                    GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
                        shaderBlend.mode.gpuLabel,
                        shaderBlend.formulaId,
                    ) == null ||
                    clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None
                ) {
                    null
                } else {
                    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverDstRead
                }
            }
            blend.nativePathCoverBlendProgramOrNull() == null -> null
            depthStencil.pathStencilStructuralProgramOrNull() ==
                GPUCorePrimitivePathStencilStructuralProgram.CoverRegular -> when (clip) {
                GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ->
                    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular
                is GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic ->
                    clip.nativeAnalyticPathCoverProgramOrNull(inverse = false).takeIf {
                        blend.isCanonicalPremulSrcOver()
                    }
                else -> null
            }
            depthStencil.pathStencilStructuralProgramOrNull() ==
                GPUCorePrimitivePathStencilStructuralProgram.CoverInverse -> when (clip) {
                GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ->
                    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse
                is GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic ->
                    clip.nativeAnalyticPathCoverProgramOrNull(inverse = true).takeIf {
                        blend.isCanonicalPremulSrcOver()
                    }
                else -> null
            }
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer -> when {
            clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ||
                shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.ClipStencilProducer ||
                topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan ||
                blend != GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone -> null
            clipStencilStructuralProgramOrNull() ==
                GPUCorePrimitiveClipStencilStructuralProgram.ProducerWinding ->
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding
            clipStencilStructuralProgramOrNull() ==
                GPUCorePrimitiveClipStencilStructuralProgram.ProducerEvenOdd ->
                GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer -> when {
            clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ||
                (shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRRect &&
                    shader != GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect &&
                    topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList) ||
                (shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRRect &&
                    topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.AnalyticRRect) ||
                (shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect &&
                    topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.AnalyticDRRect) ||
                !blend.isCanonicalPremulSrcOver() -> null
            clipStencilStructuralProgramOrNull() ==
                GPUCorePrimitiveClipStencilStructuralProgram.ConsumerRegular -> when (shader) {
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientRegular
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectRadialGradient ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientRegular
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectSweepGradient ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientRegular
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRRect ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectRegular
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectRegular
                    else -> null
                }
            clipStencilStructuralProgramOrNull() ==
                GPUCorePrimitiveClipStencilStructuralProgram.ConsumerInverse -> when (shader) {
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientInverse
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectRadialGradient ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientInverse
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectSweepGradient ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientInverse
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRRect ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectInverse
                    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticDRRect ->
                        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectInverse
                    else -> null
                }
            else -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer -> when (
            coverageMaskStructuralProgramOrNull()
        ) {
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectIntersect ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectDifference ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectIntersect ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect
            GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectDifference ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference
            GPUCorePrimitiveCoverageMaskStructuralProgram.ConsumerNearest,
            null,
            -> null
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer -> when {
            coverageMaskStructuralProgramOrNull() ==
                GPUCorePrimitiveCoverageMaskStructuralProgram.ConsumerNearest &&
                blend.isCanonicalPremulSrcOver() ->
                GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest
            else -> null
        }
    }
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.gradientProgramOrNull(
    program: GPUWgpu4kCorePrimitivePipelineProgram,
): GPUWgpu4kCorePrimitivePipelineProgram? = when {
    role != GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading ||
        topology != GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList ||
        clip != GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None ||
        depthStencil != GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None ||
        blend.nativeShadingBlendProgramOrNull() == null -> null
    else -> program
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.nativeBlendProgramOrNull(
    program: GPUWgpu4kCorePrimitivePipelineProgram,
): GPUWgpu4kCorePrimitiveBlendProgram? = when {
    program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding ||
        program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd ||
        program.isClipStencilProducer() ->
        GPUWgpu4kCorePrimitiveBlendProgram.ColorWriteNone.takeIf {
            blend == GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone
        }
    program.isAnalyticPathStencilCover() ->
        GPUWgpu4kCorePrimitiveBlendProgram.PremulSrcOver.takeIf {
            blend.isCanonicalPremulSrcOver()
        }
    program.isLegacyPathStencilCover() ->
        blend.nativePathCoverBlendProgramOrNull()
    program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverDstRead ->
        blend.nativeShadingBlendProgramOrNull()
    program == GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect ||
        program == GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect ->
        GPUWgpu4kCorePrimitiveBlendProgram.PremulDstIn.takeIf {
            blend.fixedNativeBlendProgramOrNull() ==
                GPUWgpu4kCorePrimitiveBlendProgram.PremulDstIn
        }
    program == GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference ||
        program == GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference ->
        GPUWgpu4kCorePrimitiveBlendProgram.PremulDstOut.takeIf {
            blend.fixedNativeBlendProgramOrNull() ==
                GPUWgpu4kCorePrimitiveBlendProgram.PremulDstOut
        }
    program == GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeDstRead ->
        blend.analyticShapeDstReadBlendProgramOrNull()
    else -> blend.nativeShadingBlendProgramOrNull()
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.nativeShadingBlendProgramOrNull():
    GPUWgpu4kCorePrimitiveBlendProgram? = when (this) {
    is GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed -> fixedNativeBlendProgramOrNull()
    is GPUCorePrimitiveRenderPipelineStructuralKey.Blend.NoOp ->
        GPUWgpu4kCorePrimitiveBlendProgram.DestinationNoOp.takeIf { mode == GPUBlendMode.DST }
    is GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination ->
        if (sourceCoverage != GPUSourceCoverageEncoding.None) {
            null
        } else if (
            GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(mode.gpuLabel, formulaId) == null
        ) {
            null
        } else {
            GPUWgpu4kCorePrimitiveBlendProgram.entries.singleOrNull { candidate ->
                candidate.isDstRead() && candidate.mode == mode
            }
        }
    else -> null
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.analyticShapeDstReadBlendProgramOrNull():
    GPUWgpu4kCorePrimitiveBlendProgram? {
    val shader =
        this as? GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination ?: return null
    if (shader.sourceCoverage == GPUSourceCoverageEncoding.LCDCoverageInShader) return null
    if (GPUBlendFormulaProgramLibrary.selectedFullCoverageFunctionWgsl(
            shader.mode.gpuLabel,
            shader.formulaId,
        ) == null
    ) {
        return null
    }
    return GPUWgpu4kCorePrimitiveBlendProgram.entries.singleOrNull { candidate ->
        candidate.isDstRead() && candidate.mode == shader.mode
    }
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.nativePathCoverBlendProgramOrNull():
    GPUWgpu4kCorePrimitiveBlendProgram? = when (this) {
    is GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed -> fixedNativeBlendProgramOrNull()
    is GPUCorePrimitiveRenderPipelineStructuralKey.Blend.NoOp ->
        GPUWgpu4kCorePrimitiveBlendProgram.DestinationNoOp.takeIf { mode == GPUBlendMode.DST }
    else -> null
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.fixedNativeBlendProgramOrNull():
    GPUWgpu4kCorePrimitiveBlendProgram? {
    val fixed = this as? GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed ?: return null
    return GPUWgpu4kCorePrimitiveBlendProgram.entries.singleOrNull { candidate ->
        candidate.mode == fixed.mode &&
            !candidate.isDstRead() &&
            candidate.colorSourceFactor != null &&
            fixed.sourceCoverage == GPUSourceCoverageEncoding.None &&
            fixed.state.color.sourceFactor == candidate.colorSourceFactor &&
            fixed.state.color.destinationFactor == candidate.colorDestinationFactor &&
            fixed.state.color.operation == candidate.colorOperation &&
            fixed.state.alpha.sourceFactor == candidate.alphaSourceFactor &&
            fixed.state.alpha.destinationFactor == candidate.alphaDestinationFactor &&
            fixed.state.alpha.operation == candidate.alphaOperation &&
            fixed.state.writeMask == "rgba"
    }
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.supportsFourSampleProgram(): Boolean =
    (role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading &&
        shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry) ||
        role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer ||
        role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover ||
        role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer ||
        (role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer &&
            shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry)

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic.nativeAnalyticProgramOrNull():
    GPUWgpu4kCorePrimitivePipelineProgram? = when (geometry) {
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect -> if (antiAlias) {
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA
    } else {
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectHard
    }
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect -> if (antiAlias) {
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectAA
    } else {
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectHard
    }
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Path -> null
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic
    .nativeAnalyticPathCoverProgramOrNull(
        inverse: Boolean,
    ): GPUWgpu4kCorePrimitivePipelineProgram? = when (geometry) {
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect -> when {
        antiAlias && inverse ->
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAAInverse
        antiAlias ->
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAARegular
        inverse ->
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardInverse
        else ->
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardRegular
    }
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect -> when {
        antiAlias && inverse ->
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAAInverse
        antiAlias ->
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAARegular
        inverse ->
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardInverse
        else ->
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardRegular
    }
    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Path -> null
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticClip(): Boolean = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectHard,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectHard,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectAA,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAARegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAAInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAARegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAAInverse,
    -> true
    else -> false
}

private fun GPUWgpu4kCorePrimitivePipelineProgram.isPathStencilCover(): Boolean = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAARegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAAInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAARegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAAInverse,
    -> true
    else -> false
}

private fun GPUWgpu4kCorePrimitivePipelineProgram.isLegacyPathStencilCover(): Boolean =
    this == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse

private fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticPathStencilCover(): Boolean =
    isPathStencilCover() && !isLegacyPathStencilCover()

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticShape(): Boolean =
        this == GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeSrcOver ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectRegular ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectInverse

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticShapeDstRead(): Boolean =
    this == GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeDstRead

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticDRRect(): Boolean =
    this == GPUWgpu4kCorePrimitivePipelineProgram.AnalyticDRRectSrcOver ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectRegular ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectInverse

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticShapeProgram(): Boolean =
    isAnalyticShape() || isAnalyticShapeDstRead() || isAnalyticDRRect()

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isGradient(): Boolean = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradient,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradientRepeat,
    GPUWgpu4kCorePrimitivePipelineProgram.DirectRadialGradient,
    GPUWgpu4kCorePrimitivePipelineProgram.DirectSweepGradient,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradient,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradientRepeat,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticRadialGradient,
    GPUWgpu4kCorePrimitivePipelineProgram.AnalyticSweepGradient,
    -> true
    else -> false
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isClipStencilGradient(): Boolean =
    this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientRegular ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientInverse ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientRegular ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientInverse ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientRegular ||
        this == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientInverse

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Shader.isGradient(): Boolean = when (this) {
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient,
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradientRepeat,
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectRadialGradient,
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectSweepGradient,
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradient,
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradientRepeat,
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticRadialGradient,
    GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticSweepGradient,
    -> true
    else -> false
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isAnalyticIntersection4(): Boolean =
    this == GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipIntersection4

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isClipStencilProducer(): Boolean = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd,
    -> true
    else -> false
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isCoverageMaskProducer(): Boolean = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
    -> true
    else -> false
}

internal fun GPUWgpu4kCorePrimitivePipelineProgram.isCoverageMaskConsumer(): Boolean =
    this == GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.isCanonicalPremulSrcOver(): Boolean {
    val fixed = this as? GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed ?: return false
    return fixed.mode == GPUBlendMode.SRC_OVER &&
        fixed.sourceCoverage == GPUSourceCoverageEncoding.None &&
        fixed.state.color.sourceFactor == "one" &&
        fixed.state.color.destinationFactor == "one-minus-src-alpha" &&
        fixed.state.color.operation == "add" &&
        fixed.state.alpha.sourceFactor == "one" &&
        fixed.state.alpha.destinationFactor == "one-minus-src-alpha" &&
        fixed.state.alpha.operation == "add" &&
        fixed.state.writeMask == "rgba"
}

/** Pure descriptor lowering kept separate from native allocation and cache transactions. */
internal fun corePrimitiveWgpu4kRenderPipelineDescriptor(
    identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
    shader: GPUShaderModule,
    pipelineLayout: GPUPipelineLayout,
): RenderPipelineDescriptor {
    require(isSupportedCorePrimitiveRenderPipelineIdentity(identity)) {
        "Unsupported CorePrimitive native pipeline identity: $identity"
    }
    val stencilProgram = identity.program ==
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd ||
        identity.program.isPathStencilCover() ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverDstRead ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientRegular ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientInverse ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientRegular ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientInverse ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientRegular ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientInverse ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectRegular ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectInverse ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectRegular ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectInverse
    val producer = identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding ||
        identity.program == GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd ||
        identity.program.isClipStencilProducer()
    val coverageMaskProducer = identity.program.isCoverageMaskProducer()
    return RenderPipelineDescriptor(
        label = "Kanvas.session.corePrimitive.pipeline.${identity.program.name}",
        layout = pipelineLayout,
        vertex = VertexState(
            module = shader,
            entryPoint = if (identity.program.isClipStencilProducer()) {
                CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_VERTEX_ENTRY_POINT
            } else if (identity.program.isAnalyticShapeProgram()) {
                CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_VERTEX_ENTRY_POINT
            } else if (identity.program.isGradient()) {
                CORE_PRIMITIVE_GRADIENT_NATIVE_VERTEX_ENTRY_POINT
            } else if (identity.program.isAnalyticClip()) {
                CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_VERTEX_ENTRY_POINT
            } else if (coverageMaskProducer) {
                CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_VERTEX_ENTRY_POINT
            } else if (identity.program.isCoverageMaskConsumer()) {
                CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_VERTEX_ENTRY_POINT
            } else {
                CORE_PRIMITIVE_NATIVE_VERTEX_ENTRY_POINT
            },
            buffers = if (coverageMaskProducer) emptyList() else listOf(
                VertexBufferLayout(
                    arrayStride = 8uL,
                    attributes = listOf(
                        VertexAttribute(
                            shaderLocation = 0u,
                            offset = 0uL,
                            format = GPUVertexFormat.Float32x2,
                        ),
                    ),
                ),
            ),
        ),
        primitive = PrimitiveState(
            topology = GPUPrimitiveTopology.TriangleList,
            frontFace = GPUFrontFace.CCW,
            cullMode = GPUCullMode.None,
        ),
        depthStencil = if (stencilProgram) identity.program.depthStencilState() else null,
        multisample = MultisampleState(count = identity.sampleCount.toUInt()),
        fragment = FragmentState(
            module = shader,
            entryPoint = if (identity.program.isClipStencilProducer()) {
                CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_FRAGMENT_ENTRY_POINT
            } else if (identity.program.isAnalyticShapeProgram()) {
                CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_FRAGMENT_ENTRY_POINT
            } else if (identity.program.isGradient()) {
                CORE_PRIMITIVE_GRADIENT_NATIVE_FRAGMENT_ENTRY_POINT
            } else if (identity.program.isAnalyticClip()) {
                CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_FRAGMENT_ENTRY_POINT
            } else if (identity.program.isCoverageMaskProducer()) {
                identity.program.coverageMaskProducerFragmentEntryPoint()
            } else if (identity.program.isCoverageMaskConsumer()) {
                CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_FRAGMENT_ENTRY_POINT
            } else if (producer) {
                CORE_PRIMITIVE_NATIVE_STENCIL_FRAGMENT_ENTRY_POINT
            } else {
                CORE_PRIMITIVE_NATIVE_COLOR_FRAGMENT_ENTRY_POINT
            },
            targets = listOf(
                ColorTargetState(
                    format = when (identity.targetFormat) {
                        "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
                        "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
                        "bgra8unorm" -> GPUTextureFormat.BGRA8Unorm
                        else -> error("Validated CorePrimitive target format became unsupported")
                    },
                    blend = when {
                        producer -> null
                        else -> identity.blendProgram.toWgpuBlendStateOrNull()
                    },
                    writeMask = if (identity.blendProgram.writesColor()) {
                        GPUColorWrite.All
                    } else {
                        GPUColorWrite.None
                    },
                ),
            ),
        ),
    )
}

internal fun isSupportedCorePrimitiveRenderPipelineIdentity(
    identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
): Boolean = identity.targetFormat in setOf("rgba8unorm", "rgba8unorm-srgb", "bgra8unorm") &&
    (identity.sampleCount == 1 ||
        identity.sampleCount == 4 && identity.program.supportsFourSamples()) &&
    identity.topology == "triangle-list" && identity.frontFace == "ccw" &&
    identity.cullMode == "none" && identity.hasCompatibleBlendProgram()

private fun GPUWgpu4kCorePrimitiveRenderPipelineIdentity.hasCompatibleBlendProgram(): Boolean =
    when {
        program.isAnalyticPathStencilCover() ->
            blendProgram == GPUWgpu4kCorePrimitiveBlendProgram.PremulSrcOver
        program.isLegacyPathStencilCover() ->
            blendProgram == GPUWgpu4kCorePrimitiveBlendProgram.DestinationNoOp ||
            blendProgram.mode in setOf(
                GPUBlendMode.CLEAR,
                GPUBlendMode.SRC,
                GPUBlendMode.SRC_OVER,
                GPUBlendMode.DST_OVER,
                GPUBlendMode.SRC_IN,
                GPUBlendMode.DST_IN,
                GPUBlendMode.SRC_OUT,
                GPUBlendMode.DST_OUT,
                GPUBlendMode.SRC_ATOP,
                GPUBlendMode.DST_ATOP,
                GPUBlendMode.XOR,
                GPUBlendMode.MODULATE,
                GPUBlendMode.SCREEN,
            )
        else -> when (program) {
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver,
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil,
            GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradient,
            GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradientRepeat,
            GPUWgpu4kCorePrimitivePipelineProgram.DirectRadialGradient,
            GPUWgpu4kCorePrimitivePipelineProgram.DirectSweepGradient,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradient,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradientRepeat,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticRadialGradient,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticSweepGradient,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeSrcOver,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeDstRead,
            GPUWgpu4kCorePrimitivePipelineProgram.AnalyticDRRectSrcOver,
            GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectRegular,
            GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectInverse,
            GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectRegular,
            GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectInverse,
            GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverDstRead,
            -> blendProgram.mode != null
            else -> blendProgram == program.defaultBlendProgram()
        }
    }

internal fun GPUWgpu4kCorePrimitivePipelineProgram.defaultBlendProgram():
    GPUWgpu4kCorePrimitiveBlendProgram = when (this) {
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd,
    -> GPUWgpu4kCorePrimitiveBlendProgram.ColorWriteNone
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
    -> GPUWgpu4kCorePrimitiveBlendProgram.PremulDstIn
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
    GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
    -> GPUWgpu4kCorePrimitiveBlendProgram.PremulDstOut
    else -> GPUWgpu4kCorePrimitiveBlendProgram.PremulSrcOver
}

private fun GPUWgpu4kCorePrimitivePipelineProgram.supportsFourSamples(): Boolean = when (this) {
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver,
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAARegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAAInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardRegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardInverse,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAARegular,
    GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAAInverse,
        -> true
    else -> false
}

private fun GPUWgpu4kCorePrimitivePipelineProgram.depthStencilState(): DepthStencilState {
    val (front, back, readMask, writeMask) = when (this) {
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerWinding ->
            NativeStencilState(
                face(pass = GPUStencilOperation.IncrementWrap),
                face(pass = GPUStencilOperation.DecrementWrap),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilProducerEvenOdd ->
            NativeStencilState(
                face(pass = GPUStencilOperation.Invert),
                face(pass = GPUStencilOperation.Invert),
                0xffu,
                0x01u,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding ->
            NativeStencilState(
                face(pass = GPUStencilOperation.IncrementWrap),
                face(pass = GPUStencilOperation.DecrementWrap),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerEvenOdd ->
            NativeStencilState(
                face(pass = GPUStencilOperation.Invert),
                face(pass = GPUStencilOperation.Invert),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRegular,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientRegular,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientRegular,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientRegular,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectRegular,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectRegular,
        ->
            NativeStencilState(
                face(compare = GPUCompareFunction.NotEqual, pass = GPUStencilOperation.Keep),
                face(compare = GPUCompareFunction.NotEqual, pass = GPUStencilOperation.Keep),
                0xffu,
                0u,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerLinearGradientInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerRadialGradientInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerSweepGradientInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticRRectInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilConsumerAnalyticDRRectInverse,
        ->
            NativeStencilState(
                face(compare = GPUCompareFunction.Equal, pass = GPUStencilOperation.Keep),
                face(compare = GPUCompareFunction.Equal, pass = GPUStencilOperation.Keep),
                0xffu,
                0u,
            )
        // The continued cover pass loads the fan read-only, so its pipeline must not
        // write stencil; the test alone gates the destination-read shade (no reset is needed — the
        // fan is a frame-local attachment discarded after this pass).
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverDstRead ->
            NativeStencilState(
                face(compare = GPUCompareFunction.NotEqual, pass = GPUStencilOperation.Keep),
                face(compare = GPUCompareFunction.NotEqual, pass = GPUStencilOperation.Keep),
                0xffu,
                0u,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverRegular,
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardRegular,
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAARegular,
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardRegular,
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAARegular,
        ->
            NativeStencilState(
                face(
                    compare = GPUCompareFunction.NotEqual,
                    depthFail = GPUStencilOperation.Zero,
                    pass = GPUStencilOperation.Zero,
                ),
                face(
                    compare = GPUCompareFunction.NotEqual,
                    depthFail = GPUStencilOperation.Zero,
                    pass = GPUStencilOperation.Zero,
                ),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectHardInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRectAAInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectHardInverse,
        GPUWgpu4kCorePrimitivePipelineProgram.PathStencilCoverAnalyticRRectAAInverse,
        ->
            NativeStencilState(
                face(
                    compare = GPUCompareFunction.Equal,
                    fail = GPUStencilOperation.Zero,
                    pass = GPUStencilOperation.Keep,
                ),
                face(
                    compare = GPUCompareFunction.Equal,
                    fail = GPUStencilOperation.Zero,
                    pass = GPUStencilOperation.Keep,
                ),
                0xffu,
                0xffu,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil ->
            NativeStencilState(
                face(pass = GPUStencilOperation.Keep),
                face(pass = GPUStencilOperation.Keep),
                0u,
                0u,
            )
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver ->
            error("DirectSrcOver has no depth/stencil state")
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeSrcOver,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeDstRead,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticDRRectSrcOver,
        GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradient,
        GPUWgpu4kCorePrimitivePipelineProgram.DirectLinearGradientRepeat,
        GPUWgpu4kCorePrimitivePipelineProgram.DirectRadialGradient,
        GPUWgpu4kCorePrimitivePipelineProgram.DirectSweepGradient,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradient,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticLinearGradientRepeat,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticRadialGradient,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticSweepGradient,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectHard,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRectAA,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectHard,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipRRectAA,
        GPUWgpu4kCorePrimitivePipelineProgram.AnalyticClipIntersection4,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskConsumerNearest,
        -> error("Color-only CorePrimitive programs have no depth/stencil state")
    }
    return DepthStencilState(
        format = GPUTextureFormat.Depth24PlusStencil8,
        depthWriteEnabled = false,
        depthCompare = GPUCompareFunction.Always,
        stencilFront = front,
        stencilBack = back,
        stencilReadMask = readMask,
        stencilWriteMask = writeMask,
    )
}

private data class NativeStencilState(
    val front: StencilFaceState,
    val back: StencilFaceState,
    val readMask: UInt,
    val writeMask: UInt,
)

private fun face(
    compare: GPUCompareFunction = GPUCompareFunction.Always,
    fail: GPUStencilOperation = GPUStencilOperation.Keep,
    depthFail: GPUStencilOperation = GPUStencilOperation.Keep,
    pass: GPUStencilOperation,
) = StencilFaceState(compare, fail, depthFail, pass)

private fun GPUWgpu4kCorePrimitivePipelineProgram.coverageMaskProducerFragmentEntryPoint(): String =
    when (this) {
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRectDifference,
        -> CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RECT_FRAGMENT_ENTRY_POINT
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectIntersect,
        GPUWgpu4kCorePrimitivePipelineProgram.CoverageMaskProducerRRectDifference,
        -> CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RRECT_FRAGMENT_ENTRY_POINT
        else -> error("$this is not a coverage-mask producer")
    }

private fun GPUWgpu4kCorePrimitiveBlendProgram.writesColor(): Boolean =
    this != GPUWgpu4kCorePrimitiveBlendProgram.ColorWriteNone &&
        this != GPUWgpu4kCorePrimitiveBlendProgram.DestinationNoOp

private fun GPUWgpu4kCorePrimitiveBlendProgram.toWgpuBlendStateOrNull(): BlendState? {
    if (!writesColor()) return null
    return BlendState(
        color = BlendComponent(
            requireNotNull(colorOperation).toWgpuBlendOperation(),
            requireNotNull(colorSourceFactor).toWgpuBlendFactor(),
            requireNotNull(colorDestinationFactor).toWgpuBlendFactor(),
        ),
        alpha = BlendComponent(
            requireNotNull(alphaOperation).toWgpuBlendOperation(),
            requireNotNull(alphaSourceFactor).toWgpuBlendFactor(),
            requireNotNull(alphaDestinationFactor).toWgpuBlendFactor(),
        ),
    )
}

private fun String.toWgpuBlendFactor(): GPUBlendFactor = when (this) {
    "zero" -> GPUBlendFactor.Zero
    "one" -> GPUBlendFactor.One
    "src" -> GPUBlendFactor.Src
    "one-minus-src" -> GPUBlendFactor.OneMinusSrc
    "src-alpha" -> GPUBlendFactor.SrcAlpha
    "one-minus-src-alpha" -> GPUBlendFactor.OneMinusSrcAlpha
    "dst-alpha" -> GPUBlendFactor.DstAlpha
    "one-minus-dst-alpha" -> GPUBlendFactor.OneMinusDstAlpha
    else -> error("Unsupported CorePrimitive fixed-function blend factor: $this")
}

private fun String.toWgpuBlendOperation(): GPUBlendOperation = when (this) {
    "add" -> GPUBlendOperation.Add
    else -> error("Unsupported CorePrimitive fixed-function blend operation: $this")
}
