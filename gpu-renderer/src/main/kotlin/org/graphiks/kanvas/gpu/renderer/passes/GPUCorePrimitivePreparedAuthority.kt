package org.graphiks.kanvas.gpu.renderer.passes

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

/** Compact code/layout axes computed once while the prepared packet is recorded. */
internal data class GPUCorePrimitiveRenderPipelineStructuralKey(
    val shader: Shader,
    val topology: Topology,
    val blend: Blend,
    val clip: Clip,
    val role: Role = Role.Shading,
    val frontFace: FrontFace = FrontFace.Ccw,
    val cullMode: CullMode = CullMode.None,
    val colorFormat: ColorFormat = ColorFormat.Rgba8Unorm,
    val depthStencil: DepthStencil = DepthStencil.None,
    val sampleCount: Int = 1,
    val clipStencilFillRule: GPUClipFillRule? = null,
) {
    enum class Role {
        Shading,
        PathStencilProducer,
        PathStencilCover,
        ClipStencilProducer,
        ClipStencilConsumer,
        CoverageMaskProducer,
        CoverageMaskConsumer,
    }
    enum class Shader {
        DirectGeometry,
        AnalyticShape,
        AnalyticRRect,
        PathStencil,
        ClipStencilProducer,
        CoverageMaskRectProducer,
        CoverageMaskRRectProducer,
        CoverageMaskConsumer,
    }
    enum class Topology { DirectTriangleList, AnalyticRRect, StencilEdgeFan, StrokeStencilEdgeFan }
    enum class FrontFace { Ccw }
    enum class CullMode { None }
    enum class ColorFormat { Rgba8Unorm }
    enum class DepthStencilFormat { Depth24PlusStencil8 }
    enum class ClipGeometry { Rect, RRect, Path }
    enum class UniformLayout(val stableIdentity: String) {
        DynamicUniform32V2("dynamic-uniform32-v2"),
        AnalyticShapeUniform80V1("dynamic-uniform80-analytic-shape-v1"),
        AnalyticClipUniform64V1("dynamic-uniform64-analytic-clip-v1"),
        AnalyticClipUniform160V1("dynamic-uniform160-analytic-clip-intersection4-v1"),
        NoBindingsV1("no-bindings-v1"),
        CoverageMaskProducerUniform64V1("dynamic-uniform64-coverage-mask-producer-v1"),
        CoverageMaskConsumerUniform64V1("dynamic-uniform64-coverage-mask-consumer-v1"),
    }

    /** Derived executable ABI axis. It deliberately leaves the legacy constructor and hashes intact. */
    val uniformLayout: UniformLayout
        get() = when {
            role == Role.ClipStencilProducer -> UniformLayout.NoBindingsV1
            role == Role.CoverageMaskProducer -> UniformLayout.CoverageMaskProducerUniform64V1
            role == Role.CoverageMaskConsumer -> UniformLayout.CoverageMaskConsumerUniform64V1
            role == Role.Shading && shader == Shader.AnalyticShape ->
                UniformLayout.AnalyticShapeUniform80V1
            role == Role.Shading && clip is Clip.Analytic -> UniformLayout.AnalyticClipUniform64V1
            role == Role.Shading && clip == Clip.AnalyticIntersection4 ->
                UniformLayout.AnalyticClipUniform160V1
            else -> UniformLayout.DynamicUniform32V2
        }

    data class StencilFace(
        val compare: GPUClipStencilCompare,
        val passOperation: GPUClipStencilOperation,
        val failOperation: GPUClipStencilOperation,
        val depthFailOperation: GPUClipStencilOperation,
    )

    sealed interface DepthStencil {
        data object None : DepthStencil

        data class Stencil(
            val format: DepthStencilFormat,
            val front: StencilFace,
            val back: StencilFace,
            val readMask: UInt,
            val writeMask: UInt,
        ) : DepthStencil {
            init {
                require(readMask <= 0xffu && writeMask <= 0xffu) {
                    "CorePrimitive stencil masks must fit stencil8"
                }
                require(writeMask != 0u || isDirectPathAttachmentNeutral() || isReadOnlyClipConsumer()) {
                    "A zero-write stencil state must be an exact neutral or read-only clip-consumer state"
                }
            }

            internal fun isDirectPathAttachmentNeutral(): Boolean =
                readMask == 0u && writeMask == 0u &&
                    front.isDirectPathAttachmentNeutral() && back.isDirectPathAttachmentNeutral()

            private fun StencilFace.isDirectPathAttachmentNeutral(): Boolean =
                compare == GPUClipStencilCompare.Always &&
                    passOperation == GPUClipStencilOperation.Keep &&
                    failOperation == GPUClipStencilOperation.Keep &&
                    depthFailOperation == GPUClipStencilOperation.Keep

            internal fun isReadOnlyClipConsumer(): Boolean =
                readMask == 0xffu && writeMask == 0u &&
                    front.isReadOnlyClipConsumer() && back.isReadOnlyClipConsumer()

            private fun StencilFace.isReadOnlyClipConsumer(): Boolean =
                compare in setOf(GPUClipStencilCompare.NotEqual, GPUClipStencilCompare.Equal) &&
                    passOperation == GPUClipStencilOperation.Keep &&
                    failOperation == GPUClipStencilOperation.Keep &&
                    depthFailOperation == GPUClipStencilOperation.Keep
        }
    }

    sealed interface Blend {
        data class Fixed(
            val mode: GPUBlendMode,
            val sourceCoverage: GPUSourceCoverageEncoding,
            val state: GPUFixedFunctionBlendState,
        ) : Blend

        data class ShaderNoDestination(
            val mode: GPUBlendMode,
            val formulaId: String,
            val sourceCoverage: GPUSourceCoverageEncoding,
        ) : Blend

        data class ShaderWithDestination(
            val mode: GPUBlendMode,
            val formulaId: String,
            val sourceCoverage: GPUSourceCoverageEncoding,
        ) : Blend

        data class NoOp(val mode: GPUBlendMode) : Blend
        data class Unsupported(val mode: GPUBlendMode) : Blend
        data object ColorWriteNone : Blend
    }

    sealed interface Clip {
        data object None : Clip

        data class Analytic(
            val geometry: ClipGeometry,
            val antiAlias: Boolean,
        ) : Clip

        /** Fixed-capacity analytic intersection; count, kinds, AA, and values are uniform-only. */
        data object AnalyticIntersection4 : Clip

        data class Stencil(
            val compare: GPUClipStencilCompare,
            val passOperation: GPUClipStencilOperation,
            val failOperation: GPUClipStencilOperation,
            val depthFailOperation: GPUClipStencilOperation,
            val readMask: UInt,
            val writeMask: UInt,
        ) : Clip

        data class Mask(
            val sampling: GPUClipMaskSampling,
            val invert: Boolean,
            val depthStencilRequired: Boolean,
        ) : Clip

        /** B3.3d structural token; origin, bounds, dimensions, and invert stay payload-only. */
        data object CoverageMaskNearest : Clip

        data object Refused : Clip
    }

    init {
        require(sampleCount > 0) { "CorePrimitive structural sample count must be positive" }
        require(role == Role.ClipStencilProducer || clipStencilFillRule == null) {
            "Only the CorePrimitive clip-stencil producer may retain clip fill-rule authority"
        }
        when (role) {
            Role.Shading -> require(
                depthStencil == DepthStencil.None ||
                    (depthStencil as? DepthStencil.Stencil)?.isDirectPathAttachmentNeutral() == true
            ) {
                "CorePrimitive shading keys accept only no attachment or the neutral path attachment state"
            }
            Role.PathStencilProducer,
            Role.PathStencilCover,
            -> {
                require(shader == Shader.PathStencil && depthStencil is DepthStencil.Stencil) {
                    "CorePrimitive path stencil roles require the path shader and exact stencil state"
                }
                require(sampleCount == 1) { "CorePrimitive path stencil roles are single-sample" }
            }
            Role.ClipStencilProducer -> {
                require(shader == Shader.ClipStencilProducer && depthStencil is DepthStencil.Stencil) {
                    "CorePrimitive clip-stencil producer requires the sealed producer shader and stencil state"
                }
                require(clipStencilFillRule != null && blend == Blend.ColorWriteNone && clip == Clip.None) {
                    "CorePrimitive clip-stencil producer requires fill-rule authority with no color or nested clip"
                }
                require(sampleCount == 1) { "CorePrimitive clip-stencil producer is single-sample" }
            }
            Role.ClipStencilConsumer -> {
                require(shader == Shader.DirectGeometry && depthStencil is DepthStencil.Stencil) {
                    "CorePrimitive clip-stencil consumer requires direct geometry and stencil state"
                }
                require(clipStencilFillRule == null && clip == Clip.None) {
                    "CorePrimitive clip-stencil consumer keeps fill and dynamic clip facts outside its key"
                }
                require(sampleCount == 1) { "CorePrimitive clip-stencil consumer is single-sample" }
            }
            Role.CoverageMaskProducer -> {
                require(
                    shader == Shader.CoverageMaskRectProducer ||
                        shader == Shader.CoverageMaskRRectProducer,
                ) { "CorePrimitive coverage-mask producer requires one exact analytic producer shader" }
                require(hasExactCoverageMaskFixedAxes() && clip == Clip.None) {
                    "CorePrimitive coverage-mask producer is color-only and cannot retain a nested clip"
                }
                require(blend.isCoverageMaskProducerBlend()) {
                    "CorePrimitive coverage-mask producer requires exact DstIn or DstOut composition"
                }
                require(sampleCount == 1) { "CorePrimitive coverage-mask producer is single-sample" }
            }
            Role.CoverageMaskConsumer -> {
                require(shader == Shader.CoverageMaskConsumer) {
                    "CorePrimitive coverage-mask consumer requires the exact mask consumer shader"
                }
                require(hasExactCoverageMaskFixedAxes() && clip == Clip.CoverageMaskNearest) {
                    "CorePrimitive coverage-mask consumer requires color-only nearest sampling"
                }
                require(blend == coverageMaskConsumerBlend()) {
                    "CorePrimitive coverage-mask consumer requires exact canonical premultiplied SrcOver"
                }
            }
        }
    }

    /** Stable public/dump identity. Called only by the recording builder or explicit evidence tests. */
    fun stableRenderPipelineKey(prefix: String): GPURenderPipelineKey {
        val preimage = when (role) {
            Role.Shading -> buildString {
                // Compatibility ABI: direct/analytic shading descriptors did not change in B3.2.
                append("role=shading")
                append("|shader=").append(shader.name)
                append("|layout=").append(uniformLayout.stableIdentity)
                append("|topology=").append(topology.name)
                append("|frontFace=ccw|cull=none|target=rgba8unorm")
                append("|samples=").append(sampleCount)
                append("|blend=").append(blend)
                append("|clip=").append(clip)
                if (depthStencil != DepthStencil.None) {
                    append("|depthStencil=").append(depthStencil)
                }
            }
            Role.PathStencilProducer,
            Role.PathStencilCover,
            Role.ClipStencilProducer,
            Role.ClipStencilConsumer,
            Role.CoverageMaskProducer,
            Role.CoverageMaskConsumer,
            -> buildString {
                append("role=").append(role.name)
                append("|shader=").append(shader.name)
                append("|layout=").append(uniformLayout.stableIdentity)
                append("|topology=").append(topology.name)
                append("|frontFace=").append(frontFace.name)
                append("|cull=").append(cullMode.name)
                append("|target=").append(colorFormat.name)
                append("|depthStencil=").append(depthStencil)
                append("|samples=").append(sampleCount)
                append("|blend=").append(blend)
                append("|clip=").append(clip)
                if (clipStencilFillRule != null) {
                    append("|clipStencilFillRule=").append(clipStencilFillRule.name)
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(preimage.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return GPURenderPipelineKey("$prefix.$digest")
    }
}

/** Closed set of color-only coverage-mask programs consumed by native pipeline lowering. */
internal enum class GPUCorePrimitiveCoverageMaskStructuralProgram {
    ProducerRectIntersect,
    ProducerRectDifference,
    ProducerRRectIntersect,
    ProducerRRectDifference,
    ConsumerNearest,
}

internal fun GPUCorePrimitiveRenderPipelineStructuralKey.coverageMaskStructuralProgramOrNull():
    GPUCorePrimitiveCoverageMaskStructuralProgram? = when {
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer &&
        shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.CoverageMaskRectProducer &&
        hasExactCoverageMaskFixedAxes() && clip == GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None &&
        blend.isCoverageMaskProducerBlend(GPUClipMaskCombine.Intersect) ->
        GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectIntersect
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer &&
        shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.CoverageMaskRectProducer &&
        hasExactCoverageMaskFixedAxes() && clip == GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None &&
        blend.isCoverageMaskProducerBlend(GPUClipMaskCombine.Difference) ->
        GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRectDifference
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer &&
        shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.CoverageMaskRRectProducer &&
        hasExactCoverageMaskFixedAxes() && clip == GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None &&
        blend.isCoverageMaskProducerBlend(GPUClipMaskCombine.Intersect) ->
        GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectIntersect
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer &&
        shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.CoverageMaskRRectProducer &&
        hasExactCoverageMaskFixedAxes() && clip == GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None &&
        blend.isCoverageMaskProducerBlend(GPUClipMaskCombine.Difference) ->
        GPUCorePrimitiveCoverageMaskStructuralProgram.ProducerRRectDifference
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer &&
        shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.CoverageMaskConsumer &&
        hasExactCoverageMaskFixedAxes() &&
        clip == GPUCorePrimitiveRenderPipelineStructuralKey.Clip.CoverageMaskNearest &&
        blend == coverageMaskConsumerBlend() ->
        GPUCorePrimitiveCoverageMaskStructuralProgram.ConsumerNearest
    else -> null
}

internal fun corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
    geometry: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
    combine: GPUClipMaskCombine,
): GPUCorePrimitiveRenderPipelineStructuralKey {
    require(geometry == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ||
        geometry == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
    ) { "Coverage-mask producer structural authority accepts only Rect or RRect" }
    return GPUCorePrimitiveRenderPipelineStructuralKey(
        shader = when (geometry) {
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Shader.CoverageMaskRectProducer
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Shader.CoverageMaskRRectProducer
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Path -> error("Validated above")
        },
        topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
        blend = coverageMaskProducerBlend(combine),
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer,
        depthStencil = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None,
        sampleCount = 1,
    )
}

internal fun corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(
    blendPlan: GPUBlendPlan,
): GPUCorePrimitiveRenderPipelineStructuralKey {
    require(blendPlan.isCanonicalCoverageMaskConsumerSrcOver()) {
        "Coverage-mask consumer structural authority requires canonical premultiplied SrcOver"
    }
    return GPUCorePrimitiveRenderPipelineStructuralKey(
        shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.CoverageMaskConsumer,
        topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
        blend = coverageMaskConsumerBlend(),
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.CoverageMaskNearest,
        role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer,
        depthStencil = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None,
        sampleCount = 1,
    )
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.hasExactCoverageMaskFixedAxes(): Boolean =
    topology == GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList &&
        frontFace == GPUCorePrimitiveRenderPipelineStructuralKey.FrontFace.Ccw &&
        cullMode == GPUCorePrimitiveRenderPipelineStructuralKey.CullMode.None &&
        colorFormat == GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm &&
        depthStencil == GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None &&
        sampleCount == 1 && clipStencilFillRule == null

private fun coverageMaskConsumerBlend(): GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed =
    GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
        mode = GPUBlendMode.SRC_OVER,
        sourceCoverage = GPUSourceCoverageEncoding.None,
        state = GPUFixedFunctionBlendState(
            stateId = "coverage-mask-consumer-src-over-v1",
            color = org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent(
                sourceFactor = "one",
                destinationFactor = "one-minus-src-alpha",
                operation = "add",
            ),
            alpha = org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent(
                sourceFactor = "one",
                destinationFactor = "one-minus-src-alpha",
                operation = "add",
            ),
            writeMask = "rgba",
        ),
    )

private fun GPUBlendPlan.isCanonicalCoverageMaskConsumerSrcOver(): Boolean {
    val fixed = this as? GPUBlendPlan.FixedFunctionBlend ?: return false
    return fixed.mode == GPUBlendMode.SRC_OVER &&
        fixed.sourceCoverageEncoding == GPUSourceCoverageEncoding.None &&
        fixed.state.color.sourceFactor == "one" &&
        fixed.state.color.destinationFactor == "one-minus-src-alpha" &&
        fixed.state.color.operation == "add" &&
        fixed.state.alpha.sourceFactor == "one" &&
        fixed.state.alpha.destinationFactor == "one-minus-src-alpha" &&
        fixed.state.alpha.operation == "add" && fixed.state.writeMask == "rgba"
}

private fun coverageMaskProducerBlend(
    combine: GPUClipMaskCombine,
): GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed {
    val mode = when (combine) {
        GPUClipMaskCombine.Intersect -> GPUBlendMode.DST_IN
        GPUClipMaskCombine.Difference -> GPUBlendMode.DST_OUT
    }
    val destinationFactor = when (combine) {
        GPUClipMaskCombine.Intersect -> "src-alpha"
        GPUClipMaskCombine.Difference -> "one-minus-src-alpha"
    }
    return GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
        mode = mode,
        sourceCoverage = GPUSourceCoverageEncoding.None,
        state = GPUFixedFunctionBlendState(
            stateId = "coverage-mask-${mode.gpuLabel}",
            color = org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent(
                sourceFactor = "zero",
                destinationFactor = destinationFactor,
                operation = "add",
            ),
            alpha = org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent(
                sourceFactor = "zero",
                destinationFactor = destinationFactor,
                operation = "add",
            ),
            writeMask = "rgba",
        ),
    )
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.isCoverageMaskProducerBlend(): Boolean =
    isCoverageMaskProducerBlend(GPUClipMaskCombine.Intersect) ||
        isCoverageMaskProducerBlend(GPUClipMaskCombine.Difference)

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.isCoverageMaskProducerBlend(
    combine: GPUClipMaskCombine,
): Boolean = this == coverageMaskProducerBlend(combine)

/** Exact no-op D24S8 state required when a direct draw shares a pass with path stencil draws. */
internal fun corePrimitiveDirectPathDepthStencilState():
    GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil =
    CORE_PRIMITIVE_DIRECT_PATH_DEPTH_STENCIL_STATE

/** Closed handle-free projection consumed by native lowering without importing clip semantics. */
internal enum class GPUCorePrimitivePathStencilStructuralProgram {
    ProducerWinding,
    ProducerEvenOdd,
    CoverRegular,
    CoverInverse,
}

internal fun GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.pathStencilStructuralProgramOrNull():
    GPUCorePrimitivePathStencilStructuralProgram? = when (this) {
    PATH_STENCIL_PRODUCER_WINDING_STATE -> GPUCorePrimitivePathStencilStructuralProgram.ProducerWinding
    PATH_STENCIL_PRODUCER_EVEN_ODD_STATE -> GPUCorePrimitivePathStencilStructuralProgram.ProducerEvenOdd
    PATH_STENCIL_COVER_REGULAR_STATE -> GPUCorePrimitivePathStencilStructuralProgram.CoverRegular
    PATH_STENCIL_COVER_INVERSE_STATE -> GPUCorePrimitivePathStencilStructuralProgram.CoverInverse
    else -> null
}

/** Four exact clip-stencil programs; dynamic reference, geometry, bounds, and order stay outside. */
internal enum class GPUCorePrimitiveClipStencilStructuralProgram {
    ProducerWinding,
    ProducerEvenOdd,
    ConsumerRegular,
    ConsumerInverse,
}

internal fun GPUCorePrimitiveRenderPipelineStructuralKey.clipStencilStructuralProgramOrNull():
    GPUCorePrimitiveClipStencilStructuralProgram? = when {
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer &&
        clipStencilFillRule == GPUClipFillRule.Winding &&
        depthStencil == CLIP_STENCIL_PRODUCER_WINDING_STATE ->
        GPUCorePrimitiveClipStencilStructuralProgram.ProducerWinding
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer &&
        clipStencilFillRule == GPUClipFillRule.EvenOdd &&
        depthStencil == CLIP_STENCIL_PRODUCER_EVEN_ODD_STATE ->
        GPUCorePrimitiveClipStencilStructuralProgram.ProducerEvenOdd
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer &&
        depthStencil == CLIP_STENCIL_CONSUMER_REGULAR_STATE ->
        GPUCorePrimitiveClipStencilStructuralProgram.ConsumerRegular
    role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer &&
        depthStencil == CLIP_STENCIL_CONSUMER_INVERSE_STATE ->
        GPUCorePrimitiveClipStencilStructuralProgram.ConsumerInverse
    else -> null
}

internal fun corePrimitiveClipStencilProducerRenderPipelineStructuralKey(
    fillRule: GPUClipFillRule,
): GPUCorePrimitiveRenderPipelineStructuralKey = GPUCorePrimitiveRenderPipelineStructuralKey(
    shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.ClipStencilProducer,
    topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan,
    blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone,
    clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
    role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer,
    depthStencil = when (fillRule) {
        GPUClipFillRule.Winding -> CLIP_STENCIL_PRODUCER_WINDING_STATE
        GPUClipFillRule.EvenOdd -> CLIP_STENCIL_PRODUCER_EVEN_ODD_STATE
    },
    sampleCount = 1,
    clipStencilFillRule = fillRule,
)

internal fun corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(
    inverseFill: Boolean,
    blendPlan: GPUBlendPlan,
): GPUCorePrimitiveRenderPipelineStructuralKey = GPUCorePrimitiveRenderPipelineStructuralKey(
    shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry,
    topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
    blend = blendPlan.corePrimitiveStructuralBlend(),
    clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
    role = GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer,
    depthStencil = if (inverseFill) {
        CLIP_STENCIL_CONSUMER_INVERSE_STATE
    } else {
        CLIP_STENCIL_CONSUMER_REGULAR_STATE
    },
    sampleCount = 1,
)

private fun clipStencilState(
    front: GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace,
    back: GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace,
    writeMask: UInt,
): GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil =
    GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
        format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
        front = front,
        back = back,
        readMask = 0xffu,
        writeMask = writeMask,
    )

private fun clipStencilFace(
    compare: GPUClipStencilCompare = GPUClipStencilCompare.Always,
    pass: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
) = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(
    compare = compare,
    passOperation = pass,
    failOperation = GPUClipStencilOperation.Keep,
    depthFailOperation = GPUClipStencilOperation.Keep,
)

private val CLIP_STENCIL_PRODUCER_WINDING_STATE = clipStencilState(
    front = clipStencilFace(pass = GPUClipStencilOperation.IncrementWrap),
    back = clipStencilFace(pass = GPUClipStencilOperation.DecrementWrap),
    writeMask = 0xffu,
)

private val CLIP_STENCIL_PRODUCER_EVEN_ODD_STATE = clipStencilState(
    front = clipStencilFace(pass = GPUClipStencilOperation.Invert),
    back = clipStencilFace(pass = GPUClipStencilOperation.Invert),
    writeMask = 0xffu,
)

private val CLIP_STENCIL_CONSUMER_REGULAR_STATE = clipStencilState(
    front = clipStencilFace(compare = GPUClipStencilCompare.NotEqual),
    back = clipStencilFace(compare = GPUClipStencilCompare.NotEqual),
    writeMask = 0u,
)

private val CLIP_STENCIL_CONSUMER_INVERSE_STATE = clipStencilState(
    front = clipStencilFace(compare = GPUClipStencilCompare.Equal),
    back = clipStencilFace(compare = GPUClipStencilCompare.Equal),
    writeMask = 0u,
)

private val CORE_PRIMITIVE_DIRECT_PATH_DEPTH_STENCIL_STATE = run {
    val neutralFace = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(
        compare = GPUClipStencilCompare.Always,
        passOperation = GPUClipStencilOperation.Keep,
        failOperation = GPUClipStencilOperation.Keep,
        depthFailOperation = GPUClipStencilOperation.Keep,
    )
    GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
        format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
        front = neutralFace,
        back = neutralFace,
        readMask = 0u,
        writeMask = 0u,
    )
}

internal fun corePrimitiveRenderPipelineStructuralKey(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    clipExecutionPlan: GPUClipExecutionPlan,
    blendPlan: GPUBlendPlan,
    sampleCount: Int = 1,
): GPUCorePrimitiveRenderPipelineStructuralKey = GPUCorePrimitiveRenderPipelineStructuralKey(
    shader = when (val geometry = semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect -> if (
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA
        ) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape
        } else {
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry
        }
        is GPUCorePrimitiveGeometry.RRect ->
            GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape
        is GPUCorePrimitiveGeometry.TriangulatedPath -> when (geometry.geometryMode) {
            GPUCorePrimitiveGeometryMode.DirectTriangles ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry
            GPUCorePrimitiveGeometryMode.StencilEdgeFan,
            GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
            -> GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil
        }
    },
    topology = when (val geometry = semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect ->
            GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
        is GPUCorePrimitiveGeometry.RRect ->
            GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
        is GPUCorePrimitiveGeometry.TriangulatedPath -> when (geometry.geometryMode) {
            GPUCorePrimitiveGeometryMode.DirectTriangles ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
            GPUCorePrimitiveGeometryMode.StencilEdgeFan ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan
            GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StrokeStencilEdgeFan
        }
    },
    blend = blendPlan.corePrimitiveStructuralBlend(),
    clip = clipExecutionPlan.corePrimitiveStructuralClip(),
    sampleCount = sampleCount,
)

/** Pure, handle-free structural authority for the two path stencil-cover roles. */
internal fun corePrimitivePathStencilRenderPipelineStructuralKey(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    role: GPUCorePrimitiveRenderPipelineStructuralKey.Role,
    clipExecutionPlan: GPUClipExecutionPlan,
    blendPlan: GPUBlendPlan,
): GPUCorePrimitiveRenderPipelineStructuralKey {
    require(
        role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer ||
            role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
    ) {
        "Path stencil structural authority requires a producer or cover role"
    }
    require(semantic.coverageMode == GPUCorePrimitiveCoverageMode.Stencil1x) {
        "Path stencil structural authority requires Stencil1x coverage"
    }
    require(
        clipExecutionPlan == GPUClipExecutionPlan.NoClip ||
            clipExecutionPlan is GPUClipExecutionPlan.ScissorOnly,
    ) {
        "Path stencil structural authority currently accepts only no clip or dynamic scissor"
    }
    val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath
        ?: error("Path stencil structural authority requires triangulated path geometry")
    require(geometry.geometryMode != GPUCorePrimitiveGeometryMode.DirectTriangles) {
        "Path stencil structural authority requires stencil edge-fan geometry"
    }

    return GPUCorePrimitiveRenderPipelineStructuralKey(
        role = role,
        shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.PathStencil,
        topology = when (role) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when (geometry.geometryMode) {
                GPUCorePrimitiveGeometryMode.StencilEdgeFan ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StencilEdgeFan
                GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.Topology.StrokeStencilEdgeFan
                GPUCorePrimitiveGeometryMode.DirectTriangles -> error("Validated above")
            }
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList
        },
        blend = when (role) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer ->
                GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ColorWriteNone
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover ->
                blendPlan.corePrimitiveStructuralBlend()
        },
        clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        depthStencil = pathStencilState(role, geometry.fillRule, geometry.inverseFill),
        sampleCount = 1,
    )
}

private fun pathStencilState(
    role: GPUCorePrimitiveRenderPipelineStructuralKey.Role,
    fillRule: GPUCorePrimitiveFillRule,
    inverseFill: Boolean,
): GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil {
    fun face(
        compare: GPUClipStencilCompare,
        pass: GPUClipStencilOperation,
        fail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
        depthFail: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    ) = GPUCorePrimitiveRenderPipelineStructuralKey.StencilFace(
        compare = compare,
        passOperation = pass,
        failOperation = fail,
        depthFailOperation = depthFail,
    )

    val (front, back) = when (role) {
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when (fillRule) {
            GPUCorePrimitiveFillRule.Winding ->
                face(GPUClipStencilCompare.Always, GPUClipStencilOperation.IncrementWrap) to
                    face(GPUClipStencilCompare.Always, GPUClipStencilOperation.DecrementWrap)
            GPUCorePrimitiveFillRule.EvenOdd ->
                face(GPUClipStencilCompare.Always, GPUClipStencilOperation.Invert) to
                    face(GPUClipStencilCompare.Always, GPUClipStencilOperation.Invert)
        }
        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover -> {
            val compare = if (inverseFill) GPUClipStencilCompare.Equal else GPUClipStencilCompare.NotEqual
            val fail = if (inverseFill) GPUClipStencilOperation.Zero else GPUClipStencilOperation.Keep
            val pass = if (inverseFill) GPUClipStencilOperation.Keep else GPUClipStencilOperation.Zero
            val depthFail = if (inverseFill) GPUClipStencilOperation.Keep else GPUClipStencilOperation.Zero
            face(compare, pass, fail, depthFail) to face(compare, pass, fail, depthFail)
        }
        else ->
            error("Path stencil state cannot be built for shading")
    }
    return GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.Stencil(
        format = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencilFormat.Depth24PlusStencil8,
        front = front,
        back = back,
        readMask = 0xffu,
        writeMask = when (role) {
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer -> when (fillRule) {
                GPUCorePrimitiveFillRule.Winding -> 0xffu
                GPUCorePrimitiveFillRule.EvenOdd -> 0x01u
            }
            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover -> 0xffu
        },
    )
}

private val PATH_STENCIL_PRODUCER_WINDING_STATE = pathStencilState(
    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
    GPUCorePrimitiveFillRule.Winding,
    inverseFill = false,
)

private val PATH_STENCIL_PRODUCER_EVEN_ODD_STATE = pathStencilState(
    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
    GPUCorePrimitiveFillRule.EvenOdd,
    inverseFill = false,
)

private val PATH_STENCIL_COVER_REGULAR_STATE = pathStencilState(
    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
    GPUCorePrimitiveFillRule.Winding,
    inverseFill = false,
)

private val PATH_STENCIL_COVER_INVERSE_STATE = pathStencilState(
    GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
    GPUCorePrimitiveFillRule.Winding,
    inverseFill = true,
)

private fun GPUBlendPlan.corePrimitiveStructuralBlend():
    GPUCorePrimitiveRenderPipelineStructuralKey.Blend = when (this) {
    is GPUBlendPlan.FixedFunctionBlend -> GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
        mode,
        sourceCoverageEncoding,
        state,
    )
    is GPUBlendPlan.ShaderBlendNoDstRead ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderNoDestination(
            mode,
            formulaId,
            sourceCoverageEncoding,
        )
    is GPUBlendPlan.ShaderBlendWithDstRead ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Blend.ShaderWithDestination(
            mode,
            formulaId,
            sourceCoverageEncoding,
        )
    is GPUBlendPlan.LayerCompositeBlend -> child.corePrimitiveStructuralBlend()
    is GPUBlendPlan.NoOp -> GPUCorePrimitiveRenderPipelineStructuralKey.Blend.NoOp(mode)
    is GPUBlendPlan.UnsupportedBlend -> GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Unsupported(mode)
}

private fun GPUClipExecutionPlan.corePrimitiveStructuralClip():
    GPUCorePrimitiveRenderPipelineStructuralKey.Clip = when (this) {
    GPUClipExecutionPlan.NoClip,
    is GPUClipExecutionPlan.ScissorOnly,
    -> GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None
    is GPUClipExecutionPlan.AnalyticCoverage ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Analytic(
            geometry = when (geometry) {
                is GPUClipExecutionGeometry.Rect ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
                is GPUClipExecutionGeometry.RRect ->
                    if (geometry.radii.chunked(2).all { pair -> pair == geometry.radii.take(2) } &&
                        geometry.radii.take(2).any { it == 0f }
                    ) {
                        GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
                    } else {
                        GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
                    }
                is GPUClipExecutionGeometry.Path ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Path
            },
            antiAlias = antiAlias,
        )
    is GPUClipExecutionPlan.AnalyticIntersection ->
        GPUCorePrimitiveRenderPipelineStructuralKey.Clip.AnalyticIntersection4
    is GPUClipExecutionPlan.StencilCoverage -> GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Stencil(
        compare = consumer.compare,
        passOperation = consumer.passOperation,
        failOperation = consumer.failOperation,
        depthFailOperation = consumer.depthFailOperation,
        readMask = consumer.readMask,
        writeMask = consumer.writeMask,
    )
    is GPUClipExecutionPlan.CoverageMask -> GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Mask(
        sampling = consumer.sampling,
        invert = consumer.invert,
        depthStencilRequired = depthStencilRequired,
    )
    is GPUClipExecutionPlan.Refused -> GPUCorePrimitiveRenderPipelineStructuralKey.Clip.Refused
}

/** Handle-free route predicate used by execution without depending on clip-domain concrete types. */
internal fun GPUClipExecutionPlan.isCorePrimitiveNoClipOrScissorExecution(): Boolean =
    this == GPUClipExecutionPlan.NoClip || this is GPUClipExecutionPlan.ScissorOnly

/** Builder-owned plan and bytes shared by every direct draw in one prepared pass. */
internal class GPUCorePrimitiveUniformSlabSeal(
    val plan: GPUUniformSlabPlan,
    commandIds: List<Int>,
    packedBytes: ByteArray,
) {
    private val commandIdsSnapshot = immutableList(commandIds)
    private val packedBytesSnapshot = packedBytes.copyOf()

    val commandIds: List<Int>
        get() = commandIdsSnapshot

    val drawCount: Int
        get() = commandIdsSnapshot.size

    init {
        require(commandIdsSnapshot.isNotEmpty() && commandIdsSnapshot.distinct().size == commandIdsSnapshot.size) {
            "CorePrimitive uniform slab seal requires unique draw commands"
        }
        require(plan.slots.size == commandIdsSnapshot.size) {
            "CorePrimitive uniform slab slots must match draw commands"
        }
        require(plan.totalBytes == packedBytesSnapshot.size.toLong()) {
            "CorePrimitive packed uniform bytes must match the planned slab size"
        }
    }

    fun hasExactPayloads(
        expectedCommandIds: List<Int>,
        uniformBytesByDraw: List<List<Int>>,
    ): Boolean {
        if (expectedCommandIds != commandIdsSnapshot || uniformBytesByDraw.size != plan.slots.size) return false
        return plan.slots.indices.all { index ->
            hasExactPayload(index, expectedCommandIds[index], uniformBytesByDraw[index])
        }
    }

    fun hasExactPayload(index: Int, commandId: Int, bytes: List<Int>): Boolean {
        if (index !in plan.slots.indices || commandIdsSnapshot[index] != commandId) return false
        val slot = plan.slots[index]
        return slot.payloadBytes == bytes.size.toLong() &&
            slot.alignedOffset >= 0L &&
            slot.alignedOffset <= packedBytesSnapshot.size.toLong() - bytes.size.toLong() &&
            bytes.indices.all { byteIndex ->
                packedBytesSnapshot[slot.alignedOffset.toInt() + byteIndex] == bytes[byteIndex].toByte()
            }
    }

    /** Internal zero-copy borrow valid only for the immediate queue upload. */
    fun packedBytesForUpload(): ByteArray = packedBytesSnapshot

    /** Defensive snapshot for tests and non-hot-path evidence. */
    fun packedBytesSnapshot(): ByteArray = packedBytesSnapshot.copyOf()
}

internal data class GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal(
    val slotIndex: Int,
    val sourceOrder: Int,
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutHash: String,
)

/** O(1) builder authority for one exact prepared semantic object. */
internal sealed class GPUCorePrimitivePreparedSemanticAuthority private constructor() {
    internal abstract fun matches(semantic: GPUDrawSemanticPayload.CorePrimitive): Boolean

    private class Exact(
        private val preparedSemanticReference: GPUDrawSemanticPayload.CorePrimitive,
    ) : GPUCorePrimitivePreparedSemanticAuthority() {
        override fun matches(semantic: GPUDrawSemanticPayload.CorePrimitive): Boolean =
            preparedSemanticReference === semantic
    }

    internal companion object {
        fun capture(
            semantic: GPUDrawSemanticPayload.CorePrimitive,
        ): GPUCorePrimitivePreparedSemanticAuthority =
            Exact(semantic)
    }
}

internal data class GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal(
    val slotIndex: Int,
    val sourceOrder: Int,
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val dependencyFromPreviousConsumerToken: String?,
    val semanticAuthority: GPUCorePrimitivePreparedSemanticAuthority,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutHash: String,
)

internal fun corePrimitiveCoverageMaskConsumerDependencyToken(
    packetId: GPUDrawPacketID,
    sourceOrder: Int,
): String {
    require(sourceOrder >= 0) { "Coverage-mask consumer dependency source order must be non-negative" }
    return "prepared-core-primitive.coverage-mask.consumer.$sourceOrder.${packetId.value}"
}

/** Builder-owned uniform64 slab authority shared by one exact ordered coverage-mask route. */
internal class GPUCorePrimitiveCoverageMaskUniformSlabSeal(
    val plan: GPUUniformSlabPlan,
    val contentKey: String,
    val planCanonicalIdentity: String,
    val maskResource: GPUFrameTargetRef,
    producerSlots: List<GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal>,
    consumerSlots: List<GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal>,
    packedBytes: ByteArray,
) {
    val producerSlots: List<GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal> =
        immutableList(producerSlots)
    val consumerSlots: List<GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal> =
        immutableList(consumerSlots)
    private val packedBytesSnapshot = packedBytes.copyOf()

    val producerSourceOrders: List<Int> get() = producerSlots.map { it.sourceOrder }
    val producerPacketIds: List<GPUDrawPacketID> get() = producerSlots.map { it.packetId }
    val consumerCommandIds: List<Int> get() = consumerSlots.map { it.commandId }
    val consumerPacketIds: List<GPUDrawPacketID> get() = consumerSlots.map { it.packetId }

    init {
        require(contentKey.isNotBlank() && planCanonicalIdentity.isNotBlank()) {
            "Coverage-mask uniform slab requires exact plan identities"
        }
        require(producerSlots.isNotEmpty() && consumerSlots.size >= 2) {
            "Coverage-mask uniform slab requires producers followed by at least two consumers"
        }
        require(producerSlots.map { it.slotIndex } == producerSlots.indices.toList() &&
            consumerSlots.map { it.slotIndex } ==
            consumerSlots.indices.map { it + producerSlots.size }
        ) { "Coverage-mask uniform slots must retain exact producer-then-consumer order" }
        require(producerSourceOrders.zipWithNext().all { (left, right) -> left < right } &&
            consumerSlots.map { it.sourceOrder }.zipWithNext().all { (left, right) -> left < right }
        ) { "Coverage-mask uniform slots require strict source order" }
        require(consumerSlots.mapIndexed { index, slot ->
                slot.dependencyFromPreviousConsumerToken == if (index == 0) {
                    null
                } else {
                    corePrimitiveCoverageMaskConsumerDependencyToken(slot.packetId, slot.sourceOrder)
                }
            }.all { it }
        ) { "Coverage-mask consumer slots require exact previous-consumer dependency tokens" }
        require((producerPacketIds + consumerPacketIds).distinct().size ==
            producerSlots.size + consumerSlots.size &&
            consumerCommandIds.distinct().size == consumerSlots.size
        ) { "Coverage-mask uniform slots require unique packet and consumer command identities" }
        require(plan.slots.size == producerSlots.size + consumerSlots.size &&
            plan.slots.all { it.payloadBytes == 64L } &&
            plan.totalBytes == packedBytesSnapshot.size.toLong()
        ) { "Coverage-mask uniform slab requires exact uniform64 slots and packed bytes" }
    }

    fun hasExactPayload(slotIndex: Int, expected: ByteArray): Boolean {
        val slot = plan.slots.getOrNull(slotIndex) ?: return false
        if (expected.size != 64 || slot.payloadBytes != 64L ||
            slot.alignedOffset > packedBytesSnapshot.size.toLong() - expected.size.toLong()
        ) return false
        return expected.indices.all { index ->
            packedBytesSnapshot[slot.alignedOffset.toInt() + index] == expected[index]
        }
    }

    fun packedBytesSnapshot(): ByteArray = packedBytesSnapshot.copyOf()
}

/** Immutable per-packet authority for the analytic Rect/RRect uniform80 slab. */
internal class GPUCorePrimitiveAnalyticShapeUniformSeal(
    val plan: GPUUniformSlabPlan,
    val slotIndex: Int,
    val commandId: Int,
    val packetId: GPUDrawPacketID,
    private val semanticAuthority: GPUCorePrimitivePreparedSemanticAuthority,
    val renderScissor: GPUPixelBounds,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutHash: String,
    val resourceGeneration: Long,
    payloadBytes: ByteArray,
) {
    private val payloadBytesSnapshot = payloadBytes.copyOf()
    private val slot = plan.slots.getOrNull(slotIndex)
        ?: error("Analytic shape uniform seal slot index is outside its slab plan")

    val payloadBytes: Long get() = slot.payloadBytes
    val alignedOffset: Long get() = slot.alignedOffset
    val alignmentBytes: Long get() = plan.alignmentBytes
    val deviceGeneration: Long get() = plan.deviceGeneration

    init {
        require(commandId >= 0) { "Analytic shape uniform seal command id must be non-negative" }
        require(plan.sourceLabel == "core-primitive-analytic-shape-uniform-pass" &&
            slot.slotLabel == "analytic-shape-draw-$commandId"
        ) { "Analytic shape uniform seal requires its exact pass and slot labels" }
        require(!renderScissor.isEmpty) { "Analytic shape render scissor must not be empty" }
        require(structuralPipelineKey.role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading &&
            structuralPipelineKey.shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape &&
            structuralPipelineKey.uniformLayout ==
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1
        ) { "Analytic shape uniform seal requires the uniform80 shading structural ABI" }
        require(bindingLayoutHash.isNotBlank()) {
            "Analytic shape binding layout hash must not be blank"
        }
        require(resourceGeneration >= 0L) {
            "Analytic shape resource generation must be non-negative"
        }
        require(slot.payloadBytes == 80L && payloadBytesSnapshot.size == 80) {
            "Analytic shape uniform seal requires exactly 80 payload bytes"
        }
    }

    fun hasExactSemantic(semantic: GPUDrawSemanticPayload.CorePrimitive): Boolean =
        semanticAuthority.matches(semantic)

    fun payloadBytesSnapshot(): ByteArray = payloadBytesSnapshot.copyOf()

    internal fun hasExactPayload(expected: ByteArray): Boolean =
        expected.size == 80 && payloadBytesSnapshot.contentEquals(expected)

    internal fun hasExactPayloadAt(source: ByteArray, sourceOffset: Int): Boolean {
        if (sourceOffset < 0 || sourceOffset > source.size - payloadBytesSnapshot.size) return false
        return payloadBytesSnapshot.indices.all { index ->
            source[sourceOffset + index] == payloadBytesSnapshot[index]
        }
    }

    /** Copies the immutable payload into one pass-owned packed slab without an intermediate snapshot. */
    internal fun copyPayloadInto(destination: ByteArray, destinationOffset: Int) {
        require(destinationOffset >= 0 &&
            destinationOffset <= destination.size - payloadBytesSnapshot.size
        ) { "Analytic shape uniform payload does not fit its pass-owned packed slab" }
        payloadBytesSnapshot.copyInto(destination, destinationOffset)
    }
}

/** Immutable per-packet authority for the separate analytic-clip uniform64 slab. */
internal class GPUCorePrimitiveAnalyticClipUniformSeal(
    val plan: GPUUniformSlabPlan,
    val slotIndex: Int,
    val commandId: Int,
    val packetId: GPUDrawPacketID,
    val clipCanonicalIdentity: String,
    val clipType: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
    clipBounds: List<Float>,
    clipRadii: List<Float>,
    val antiAlias: Boolean,
    val conservativeScissor: GPUPixelBounds,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutHash: String,
    val resourceGeneration: Long,
    payloadBytes: ByteArray,
) {
    private val clipBoundsSnapshot = immutableList(clipBounds)
    private val clipRadiiSnapshot = immutableList(clipRadii)
    private val payloadBytesSnapshot = payloadBytes.copyOf()
    private val slot = plan.slots.getOrNull(slotIndex)
        ?: error("Analytic clip uniform seal slot index is outside its slab plan")

    val clipBounds: List<Float>
        get() = clipBoundsSnapshot
    val clipRadii: List<Float>
        get() = clipRadiiSnapshot
    val payloadBytes: Long
        get() = slot.payloadBytes
    val alignedOffset: Long
        get() = slot.alignedOffset
    val alignmentBytes: Long
        get() = plan.alignmentBytes
    val deviceGeneration: Long
        get() = plan.deviceGeneration

    init {
        require(commandId >= 0) { "Analytic clip uniform seal command id must be non-negative" }
        require(clipCanonicalIdentity.isNotBlank()) { "Analytic clip canonical identity must not be blank" }
        require(clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ||
            clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
        ) { "Analytic clip uniform seal accepts only rect or rrect" }
        require(clipBoundsSnapshot.size == 4 && clipBoundsSnapshot.all(Float::isFinite)) {
            "Analytic clip bounds require four finite scalars"
        }
        require(clipRadiiSnapshot.size == 8 && clipRadiiSnapshot.all { it.isFinite() && it >= 0f }) {
            "Analytic clip radii require four finite non-negative pairs"
        }
        require(!conservativeScissor.isEmpty) { "Analytic clip conservative scissor must not be empty" }
        require(structuralPipelineKey.uniformLayout ==
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform64V1
        ) { "Analytic clip uniform seal requires the uniform64 structural ABI" }
        require(bindingLayoutHash.isNotBlank()) { "Analytic clip binding layout hash must not be blank" }
        require(resourceGeneration >= 0L) { "Analytic clip resource generation must be non-negative" }
        require(slot.payloadBytes == 64L && payloadBytesSnapshot.size == 64) {
            "Analytic clip uniform seal requires exactly 64 payload bytes"
        }
    }

    fun payloadBytesSnapshot(): ByteArray = payloadBytesSnapshot.copyOf()

    internal fun hasExactPayload(expected: ByteArray): Boolean =
        expected.size == 64 && payloadBytesSnapshot.contentEquals(expected)
}

/** One immutable, packet-local element retained by the uniform160 seal. */
internal class GPUCorePrimitiveAnalyticIntersectionElementSeal(
    val clipType: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
    clipBounds: List<Float>,
    clipRadii: List<Float>,
    val antiAlias: Boolean,
) {
    val clipBounds: List<Float> = immutableList(clipBounds)
    val clipRadii: List<Float> = immutableList(clipRadii)

    init {
        require(clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ||
            clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
        ) { "Analytic intersection elements accept only rect or rrect" }
        require(this.clipBounds.size == 4 && this.clipBounds.all(Float::isFinite)) {
            "Analytic intersection element bounds require four finite scalars"
        }
        require(this.clipRadii.size == 2 && this.clipRadii.all { it.isFinite() && it >= 0f }) {
            "Analytic intersection element radii require one finite non-negative pair"
        }
        require(clipType != GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ||
            this.clipRadii.all { it == 0f }
        ) { "Analytic intersection rect elements must retain zero radii" }
    }

    override fun equals(other: Any?): Boolean = this === other ||
        other is GPUCorePrimitiveAnalyticIntersectionElementSeal &&
        clipType == other.clipType && clipBounds == other.clipBounds &&
        clipRadii == other.clipRadii && antiAlias == other.antiAlias

    override fun hashCode(): Int = listOf(clipType, clipBounds, clipRadii, antiAlias).hashCode()
}

/** Immutable per-packet authority for the fixed-capacity analytic-intersection uniform160 slab. */
internal class GPUCorePrimitiveAnalyticIntersectionUniformSeal(
    val plan: GPUUniformSlabPlan,
    val slotIndex: Int,
    val commandId: Int,
    val packetId: GPUDrawPacketID,
    val clipCanonicalIdentity: String,
    elements: List<GPUCorePrimitiveAnalyticIntersectionElementSeal>,
    val conservativeScissor: GPUPixelBounds,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutHash: String,
    val resourceGeneration: Long,
    payloadBytes: ByteArray,
) {
    val elements: List<GPUCorePrimitiveAnalyticIntersectionElementSeal> = immutableList(elements)
    private val payloadBytesSnapshot = payloadBytes.copyOf()
    private val slot = plan.slots.getOrNull(slotIndex)
        ?: error("Analytic intersection uniform seal slot index is outside its slab plan")

    val payloadBytes: Long get() = slot.payloadBytes
    val alignedOffset: Long get() = slot.alignedOffset
    val alignmentBytes: Long get() = plan.alignmentBytes
    val deviceGeneration: Long get() = plan.deviceGeneration

    init {
        require(commandId >= 0) { "Analytic intersection uniform seal command id must be non-negative" }
        require(clipCanonicalIdentity.isNotBlank()) { "Analytic intersection canonical identity must not be blank" }
        require(this.elements.size in 2..4) { "Analytic intersection uniform seal requires two to four elements" }
        require(!conservativeScissor.isEmpty) { "Analytic intersection conservative scissor must not be empty" }
        require(structuralPipelineKey.uniformLayout ==
            GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticClipUniform160V1
        ) { "Analytic intersection uniform seal requires the uniform160 structural ABI" }
        require(bindingLayoutHash.isNotBlank()) { "Analytic intersection binding layout hash must not be blank" }
        require(resourceGeneration >= 0L) { "Analytic intersection resource generation must be non-negative" }
        require(slot.payloadBytes == 160L && payloadBytesSnapshot.size == 160) {
            "Analytic intersection uniform seal requires exactly 160 payload bytes"
        }
    }

    fun payloadBytesSnapshot(): ByteArray = payloadBytesSnapshot.copyOf()

    internal fun hasExactPayload(expected: ByteArray): Boolean =
        expected.size == 160 && payloadBytesSnapshot.contentEquals(expected)
}

/** One-shot authority attached by the prepared-frame builder before the packet escapes. */
internal data class GPUCorePrimitivePreparedPacketAuthority(
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val renderPipelineKey: GPURenderPipelineKey,
    val uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?,
    val analyticShapeUniformSeal: GPUCorePrimitiveAnalyticShapeUniformSeal? = null,
    val analyticClipUniformSeal: GPUCorePrimitiveAnalyticClipUniformSeal? = null,
    val analyticIntersectionUniformSeal: GPUCorePrimitiveAnalyticIntersectionUniformSeal? = null,
    val coverageMaskUniformSlabSeal: GPUCorePrimitiveCoverageMaskUniformSlabSeal? = null,
)
