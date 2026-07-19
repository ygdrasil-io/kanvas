package org.graphiks.kanvas.gpu.renderer.recording

import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.passes.validateCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedSemanticAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskAttachmentFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskConsumerInput
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedCandidateDecision
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageSampleAuthority
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveCoverageMaskConsumerDependencyToken
import org.graphiks.kanvas.gpu.renderer.passes.snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.passes.validateCorePrimitiveCoverageSampleAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskPreparedRouteRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticClipUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticIntersectionElementSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticIntersectionUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilConsumerDependencyToken
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilConsumerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.geometry.corePrimitiveClipStencilEdgeFan
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilNativePathOrNull
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilNdcVertices
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilProducerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitivePathStencilRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveDirectPathDepthStencilState
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_FILL_RECT_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_FILL_RRECT_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_AFFINE_FILL_RECT_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

const val CORE_PRIMITIVE_RENDER_PIPELINE_KEY = "pipeline.core-primitive.structural-v1"
const val CORE_PRIMITIVE_BINDING_LAYOUT_HASH = "layout.core-primitive.dynamic-uniform32-v2"
const val CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH =
    "layout.core-primitive.dynamic-uniform80-analytic-shape-v1"
const val CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH =
    "layout.core-primitive.dynamic-uniform64-analytic-clip-v1"
const val CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH =
    "layout.core-primitive.dynamic-uniform160-analytic-clip-intersection4-v1"
const val CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_BINDING_LAYOUT_HASH =
    "layout.core-primitive.coverage-mask-producer.uniform64-v1"
const val CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_BINDING_LAYOUT_HASH =
    "layout.core-primitive.coverage-mask-consumer.uniform64-texture2d-v1"
const val CORE_PRIMITIVE_TARGET_STATE_HASH = "target.rgba8unorm.single-sample"
const val CORE_PRIMITIVE_VERTEX_SOURCE_LABEL = "core-primitive-device-geometry"
const val CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL = "opaque-white"

internal sealed interface GPUCorePrimitiveDirectClipAuthority {
    data class Accepted(val scissor: GPUPixelBounds) : GPUCorePrimitiveDirectClipAuthority
    data object Refused : GPUCorePrimitiveDirectClipAuthority
}

internal fun corePrimitiveDirectClipAuthority(
    plan: GPUClipExecutionPlan,
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveDirectClipAuthority = when (plan) {
    GPUClipExecutionPlan.NoClip -> GPUCorePrimitiveDirectClipAuthority.Accepted(targetBounds)
    is GPUClipExecutionPlan.ScissorOnly -> GPUCorePrimitiveDirectClipAuthority.Accepted(plan.scissor)
    is GPUClipExecutionPlan.AnalyticCoverage -> when (
        val authority = corePrimitiveAnalyticClipAuthority(plan, targetBounds)
    ) {
        is GPUCorePrimitiveAnalyticClipAuthority.Accepted ->
            GPUCorePrimitiveDirectClipAuthority.Accepted(authority.conservativeScissor)
        is GPUCorePrimitiveAnalyticClipAuthority.Refused -> GPUCorePrimitiveDirectClipAuthority.Refused
    }
    is GPUClipExecutionPlan.AnalyticIntersection -> when (
        val authority = corePrimitiveAnalyticIntersectionAuthority(plan, targetBounds)
    ) {
        is GPUCorePrimitiveAnalyticIntersectionAuthority.Accepted ->
            GPUCorePrimitiveDirectClipAuthority.Accepted(authority.conservativeScissor)
        is GPUCorePrimitiveAnalyticIntersectionAuthority.Refused -> GPUCorePrimitiveDirectClipAuthority.Refused
    }
    else -> GPUCorePrimitiveDirectClipAuthority.Refused
}

internal fun classifyCorePrimitiveDirectNativeRoute(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    clipExecutionPlan: GPUClipExecutionPlan,
    blendPlan: GPUBlendPlan?,
    samplePlan: GPUSamplePlan,
    targetFormat: String,
): GPUCorePrimitiveDirectNativeRoute {
    val exactClipScissor = (corePrimitiveDirectClipAuthority(
        clipExecutionPlan,
        semantic.targetBounds,
    ) as? GPUCorePrimitiveDirectClipAuthority.Accepted)?.scissor
    return validateCorePrimitiveDirectNativeRoute(
        semantic = semantic,
        exactClipScissor = exactClipScissor,
        canonicalPremultipliedSrcOver = blendPlan.isCanonicalSolidRectSrcOver(),
        samplePlan = samplePlan,
        targetFormat = targetFormat,
    )
}

internal sealed interface GPUCorePrimitiveAnalyticClipAuthority {
    data class Accepted(
        val clipType: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
        val bounds: List<Float>,
        val radii: List<Float>,
        val packedRadii: List<Float>,
        val antiAlias: Boolean,
        val conservativeScissor: GPUPixelBounds,
    ) : GPUCorePrimitiveAnalyticClipAuthority

    data class Refused(val code: String, val message: String) : GPUCorePrimitiveAnalyticClipAuthority
}

/** Exact packet-local analytic facts projected for pure preflight without a clip-domain import. */
internal data class GPUCorePrimitiveAnalyticClipPacketAuthority(
    val clip: GPUCorePrimitiveAnalyticClipAuthority.Accepted,
    val canonicalIdentity: String,
)

internal fun corePrimitiveAnalyticClipPacketAuthority(
    packet: GPUDrawPacket,
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveAnalyticClipPacketAuthority? {
    val plan = packet.clipExecutionPlan as? GPUClipExecutionPlan.AnalyticCoverage ?: return null
    val clip = corePrimitiveAnalyticClipAuthority(plan, targetBounds) as?
        GPUCorePrimitiveAnalyticClipAuthority.Accepted ?: return null
    return GPUCorePrimitiveAnalyticClipPacketAuthority(clip, plan.canonicalIdentity())
}

internal fun corePrimitiveAnalyticClipAuthority(
    plan: GPUClipExecutionPlan.AnalyticCoverage,
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveAnalyticClipAuthority {
    val bounds = when (val geometry = plan.geometry) {
        is GPUClipExecutionGeometry.Rect -> listOf(
            geometry.bounds.left,
            geometry.bounds.top,
            geometry.bounds.right,
            geometry.bounds.bottom,
        )
        is GPUClipExecutionGeometry.RRect -> listOf(
            geometry.bounds.left,
            geometry.bounds.top,
            geometry.bounds.right,
            geometry.bounds.bottom,
        )
        is GPUClipExecutionGeometry.Path -> error("AnalyticCoverage constructor rejects path geometry")
    }
    val sourceRadii = (plan.geometry as? GPUClipExecutionGeometry.RRect)?.radii ?: List(8) { 0f }
    val firstRadiusPair = sourceRadii.take(2)
    if (sourceRadii.chunked(2).any { pair -> pair != firstRadiusPair }) {
        return GPUCorePrimitiveAnalyticClipAuthority.Refused(
            "unsupported.recording.core_primitive_analytic_clip_complex_rrect",
            "Prepared analytic clip accepts one rrect whose four (rx, ry) corner pairs are identical.",
        )
    }
    val rectDegenerateRadius = firstRadiusPair.any { it == 0f }
    if (!rectDegenerateRadius && (firstRadiusPair[0] * 2f > bounds[2] - bounds[0] ||
            firstRadiusPair[1] * 2f > bounds[3] - bounds[1])
    ) {
        return GPUCorePrimitiveAnalyticClipAuthority.Refused(
            "unsupported.recording.core_primitive_analytic_clip_incompatible_radii",
            "Prepared analytic clip requires rx/ry to fit the rrect half extents.",
        )
    }
    val clipType = if (plan.geometry is GPUClipExecutionGeometry.Rect || rectDegenerateRadius) {
        GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
    } else {
        GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
    }
    val canonicalRadii = if (clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect) {
        List(8) { 0f }
    } else {
        sourceRadii
    }
    val expansion = if (plan.antiAlias) 0.5f else 0f
    val analyticScissor = GPUPixelBounds(
        floor(bounds[0] - expansion).toInt().coerceAtLeast(targetBounds.left),
        floor(bounds[1] - expansion).toInt().coerceAtLeast(targetBounds.top),
        ceil(bounds[2] + expansion).toInt().coerceAtMost(targetBounds.right),
        ceil(bounds[3] + expansion).toInt().coerceAtMost(targetBounds.bottom),
    )
    val scissorLeft = plan.scissor?.let { maxOf(analyticScissor.left, it.left, targetBounds.left) }
        ?: analyticScissor.left
    val scissorTop = plan.scissor?.let { maxOf(analyticScissor.top, it.top, targetBounds.top) }
        ?: analyticScissor.top
    val scissorRight = plan.scissor?.let { minOf(analyticScissor.right, it.right, targetBounds.right) }
        ?: analyticScissor.right
    val scissorBottom = plan.scissor?.let { minOf(analyticScissor.bottom, it.bottom, targetBounds.bottom) }
        ?: analyticScissor.bottom
    if (scissorRight <= scissorLeft || scissorBottom <= scissorTop) {
        return GPUCorePrimitiveAnalyticClipAuthority.Refused(
            "unsupported.recording.core_primitive_analytic_clip_scissor",
            "Prepared analytic clip and its conservative scissor must overlap the target.",
        )
    }
    val conservativeScissor = GPUPixelBounds(scissorLeft, scissorTop, scissorRight, scissorBottom)
    return GPUCorePrimitiveAnalyticClipAuthority.Accepted(
        clipType = clipType,
        bounds = bounds,
        radii = canonicalRadii,
        packedRadii = listOf(canonicalRadii[0], canonicalRadii[1], 0f, 0f),
        antiAlias = plan.antiAlias,
        conservativeScissor = conservativeScissor,
    )
}

internal fun corePrimitiveAnalyticClipUniformBytes(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    authority: GPUCorePrimitiveAnalyticClipAuthority.Accepted,
): ByteArray = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
    putFloat(semantic.targetBounds.width.toFloat())
    putFloat(semantic.targetBounds.height.toFloat())
    putInt(if (authority.clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect) 0 else 1)
    putInt(if (authority.antiAlias) 1 else 0)
    semantic.premultipliedRgba.forEach(::putFloat)
    authority.bounds.forEach(::putFloat)
    authority.packedRadii.forEach(::putFloat)
}.array()

internal class GPUCorePrimitiveAnalyticIntersectionElementAuthority(
    val clipType: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry,
    bounds: List<Float>,
    packedRadii: List<Float>,
    val antiAlias: Boolean,
) {
    val bounds: List<Float> = immutableList(bounds)
    val packedRadii: List<Float> = immutableList(packedRadii)

    init {
        require(clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect ||
            clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
        ) { "Analytic intersection authority accepts only rect or rrect" }
        require(this.bounds.size == 4 && this.bounds.all(Float::isFinite)) {
            "Analytic intersection authority bounds require four finite scalars"
        }
        require(this.packedRadii.size == 2 && this.packedRadii.all { it.isFinite() && it >= 0f }) {
            "Analytic intersection authority radii require one finite non-negative pair"
        }
    }
}

internal sealed interface GPUCorePrimitiveAnalyticIntersectionAuthority {
    class Accepted(
        elements: List<GPUCorePrimitiveAnalyticIntersectionElementAuthority>,
        val conservativeScissor: GPUPixelBounds,
    ) : GPUCorePrimitiveAnalyticIntersectionAuthority {
        val elements: List<GPUCorePrimitiveAnalyticIntersectionElementAuthority> = immutableList(elements)

        init {
            require(this.elements.size in 2..4) { "Analytic intersection authority requires depth two to four" }
            require(!conservativeScissor.isEmpty) { "Analytic intersection authority scissor must not be empty" }
        }
    }

    data class Refused(val code: String, val message: String) : GPUCorePrimitiveAnalyticIntersectionAuthority
}

internal data class GPUCorePrimitiveAnalyticIntersectionPacketAuthority(
    val clip: GPUCorePrimitiveAnalyticIntersectionAuthority.Accepted,
    val canonicalIdentity: String,
)

internal fun corePrimitiveAnalyticIntersectionPacketAuthority(
    packet: GPUDrawPacket,
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveAnalyticIntersectionPacketAuthority? {
    val plan = packet.clipExecutionPlan as? GPUClipExecutionPlan.AnalyticIntersection ?: return null
    val clip = corePrimitiveAnalyticIntersectionAuthority(plan, targetBounds) as?
        GPUCorePrimitiveAnalyticIntersectionAuthority.Accepted ?: return null
    return GPUCorePrimitiveAnalyticIntersectionPacketAuthority(clip, plan.canonicalIdentity())
}

internal fun corePrimitiveAnalyticIntersectionAuthority(
    plan: GPUClipExecutionPlan.AnalyticIntersection,
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveAnalyticIntersectionAuthority {
    var left = targetBounds.left
    var top = targetBounds.top
    var right = targetBounds.right
    var bottom = targetBounds.bottom
    val elements = mutableListOf<GPUCorePrimitiveAnalyticIntersectionElementAuthority>()
    plan.elements.forEach { element ->
        val geometry = element.geometry
        val geometryBounds = when (geometry) {
            is GPUClipExecutionGeometry.Rect -> geometry.bounds
            is GPUClipExecutionGeometry.RRect -> geometry.bounds
            is GPUClipExecutionGeometry.Path -> error("GPUClipAnalyticElement constructor rejects path geometry")
        }
        val bounds = listOf(geometryBounds.left, geometryBounds.top, geometryBounds.right, geometryBounds.bottom)
        val sourceRadii = (geometry as? GPUClipExecutionGeometry.RRect)?.radii ?: List(8) { 0f }
        val firstPair = sourceRadii.take(2)
        if (sourceRadii.chunked(2).any { pair -> pair != firstPair }) {
            return GPUCorePrimitiveAnalyticIntersectionAuthority.Refused(
                "unsupported.recording.core_primitive_analytic_intersection_complex_rrect",
                "Prepared analytic intersections require four identical rrect radius pairs.",
            )
        }
        val zeroAxis = firstPair.any { radius -> radius == 0f }
        if (!zeroAxis && (firstPair[0] * 2f > geometryBounds.right - geometryBounds.left ||
                firstPair[1] * 2f > geometryBounds.bottom - geometryBounds.top)
        ) {
            return GPUCorePrimitiveAnalyticIntersectionAuthority.Refused(
                "unsupported.recording.core_primitive_analytic_intersection_incompatible_radii",
                "Prepared analytic intersections require radii to fit each rrect half extent.",
            )
        }
        val clipType = if (geometry is GPUClipExecutionGeometry.Rect || zeroAxis) {
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
        } else {
            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
        }
        val packedRadii = if (clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect) {
            listOf(0f, 0f)
        } else {
            firstPair
        }
        elements += GPUCorePrimitiveAnalyticIntersectionElementAuthority(
            clipType,
            immutableList(bounds),
            immutableList(packedRadii),
            element.antiAlias,
        )
        val expansion = if (element.antiAlias) 0.5f else 0f
        left = maxOf(left, floor(geometryBounds.left - expansion).toInt())
        top = maxOf(top, floor(geometryBounds.top - expansion).toInt())
        right = minOf(right, ceil(geometryBounds.right + expansion).toInt())
        bottom = minOf(bottom, ceil(geometryBounds.bottom + expansion).toInt())
    }
    if (right <= left || bottom <= top) {
        return GPUCorePrimitiveAnalyticIntersectionAuthority.Refused(
            "unsupported.recording.core_primitive_analytic_intersection_scissor",
            "Prepared analytic intersection and its conservative scissor must overlap the target.",
        )
    }
    return GPUCorePrimitiveAnalyticIntersectionAuthority.Accepted(
        elements,
        GPUPixelBounds(left, top, right, bottom),
    )
}

internal fun corePrimitiveAnalyticIntersectionUniformBytes(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    authority: GPUCorePrimitiveAnalyticIntersectionAuthority.Accepted,
): ByteArray = ByteBuffer.allocate(160).order(ByteOrder.LITTLE_ENDIAN).apply {
    putFloat(semantic.targetBounds.width.toFloat())
    putFloat(semantic.targetBounds.height.toFloat())
    putInt(authority.elements.size)
    putInt(0)
    semantic.premultipliedRgba.forEach(::putFloat)
    repeat(4) { index ->
        authority.elements.getOrNull(index)?.let { element ->
            element.bounds.forEach(::putFloat)
            element.packedRadii.forEach(::putFloat)
            putInt(if (element.clipType == GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect) 0 else 1)
            putInt(if (element.antiAlias) 1 else 0)
        } ?: repeat(32) { put(0.toByte()) }
    }
}.array()

internal fun corePrimitiveRenderPipelineKey(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    clipExecutionPlan: GPUClipExecutionPlan,
    blendPlan: GPUBlendPlan,
): GPURenderPipelineKey = corePrimitiveRenderPipelineStructuralKey(
    semantic,
    clipExecutionPlan,
    blendPlan,
).stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)

internal fun corePrimitiveClipProducerPipelineKey(
    plan: GPUClipExecutionPlan,
    authority: GPUClipProducerAuthority,
): GPURenderPipelineKey {
    val facts = when (authority) {
        is GPUClipProducerAuthority.Stencil -> listOf(
            "role=clip-stencil-producer",
            "shader=clip-stencil-v1",
            "layout=layout.clip-stencil.none-v1",
            "topology=${authority.producer.geometry.pipelineTopologyIdentity()}",
            "frontFace=ccw",
            "cull=none",
            "target=rgba8unorm",
            "samples=${(plan as? GPUClipExecutionPlan.StencilCoverage)?.sampleCount ?: "invalid-plan"}",
            "blend=color-write-none",
            "clipAbi=${authority.producer.pipelineStateIdentity()}",
        )
        is GPUClipProducerAuthority.Mask -> listOf(
            "role=clip-mask-producer",
            "shader=clip-mask-v1",
            "layout=layout.clip-mask.none-v1",
            "topology=${authority.producer.geometry.pipelineTopologyIdentity()}",
            "frontFace=ccw",
            "cull=none",
            "target=rgba8unorm",
            "samples=${(plan as? GPUClipExecutionPlan.CoverageMask)?.sampleCount ?: "invalid-plan"}",
            "blend=mask-${authority.producer.combine.name.lowercase()}",
            "clipAbi=aa-${authority.producer.antiAlias}",
        )
    }
    return GPURenderPipelineKey("$CORE_PRIMITIVE_RENDER_PIPELINE_KEY.${sha256(facts.joinToString("|"))}")
}

private fun GPUCorePrimitiveGeometry.pipelineTopologyIdentity(): String = when (this) {
    is GPUCorePrimitiveGeometry.Rect -> "triangle-list-device-xy-v1"
    is GPUCorePrimitiveGeometry.RRect -> "analytic-rrect-device-xy-v1"
    is GPUCorePrimitiveGeometry.TriangulatedPath -> when (geometryMode) {
        GPUCorePrimitiveGeometryMode.DirectTriangles -> "triangle-list-device-xy-v1"
        GPUCorePrimitiveGeometryMode.StencilEdgeFan,
        GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
        -> "${geometryMode.name.lowercase()}-triangle-list-device-xy-v1"
    }
}

private fun GPUClipExecutionGeometry.pipelineTopologyIdentity(): String = when (this) {
    is GPUClipExecutionGeometry.Rect -> "rect-triangle-list-v1"
    is GPUClipExecutionGeometry.RRect -> "rrect-analytic-v1"
    is GPUClipExecutionGeometry.Path -> "path-edge-fan-v1"
}

private fun org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan.pipelineStateIdentity(): String =
    listOf(
        fillRule.name,
        compare.name,
        frontPassOperation.name,
        backPassOperation.name,
        failOperation.name,
        depthFailOperation.name,
        readMask.toString(),
        writeMask.toString(),
    ).joinToString("-")

private fun org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan.pipelineStateIdentity(): String =
    listOf(
        compare.name,
        passOperation.name,
        failOperation.name,
        depthFailOperation.name,
        readMask.toString(),
        writeMask.toString(),
    ).joinToString("-")

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun corePrimitiveColorWriteNoneBlendPlan(): GPUBlendPlan.FixedFunctionBlend =
    GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC,
        state = GPUFixedFunctionBlendState(
            stateId = "core-primitive-color-write-none",
            color = GPUFixedFunctionBlendComponent("zero", "one", "add"),
            alpha = GPUFixedFunctionBlendComponent("zero", "one", "add"),
            writeMask = "none",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

private fun corePrimitiveClipProducerBlendPlan(
    authority: GPUClipProducerAuthority,
): GPUBlendPlan.FixedFunctionBlend = when (authority) {
    is GPUClipProducerAuthority.Stencil -> corePrimitiveColorWriteNoneBlendPlan()
    is GPUClipProducerAuthority.Mask -> {
        val difference = authority.producer.combine == org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine.Difference
        GPUBlendPlan.FixedFunctionBlend(
            mode = if (difference) GPUBlendMode.DST_OUT else GPUBlendMode.DST_IN,
            state = GPUFixedFunctionBlendState(
                stateId = if (difference) "core-primitive-mask-dst-out" else "core-primitive-mask-dst-in",
                color = GPUFixedFunctionBlendComponent(
                    "zero",
                    if (difference) "one-minus-src-alpha" else "src-alpha",
                    "add",
                ),
                alpha = GPUFixedFunctionBlendComponent(
                    "zero",
                    if (difference) "one-minus-src-alpha" else "src-alpha",
                    "add",
                ),
                writeMask = "rgba",
            ),
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        )
    }
}

internal fun corePrimitiveTargetDescriptor(bounds: GPUPixelBounds): GPUFrameTextureDescriptor =
    GPUFrameTextureDescriptor(bounds, GPUColorFormat("rgba8unorm"), 1)

internal fun corePrimitiveTargetByteSize(bounds: GPUPixelBounds): Long =
    Math.multiplyExact(Math.multiplyExact(bounds.width.toLong(), bounds.height.toLong()), 4L)

internal fun corePrimitiveDepthStencilByteSize(bounds: GPUPixelBounds, sampleCount: Int): Long =
    Math.multiplyExact(corePrimitiveTargetByteSize(bounds), sampleCount.toLong())

private data class GPUCorePrimitiveDirectGeometryBytes(
    val vertexBytes: Long,
    val indexBytes: Long,
)

private data class GPUCorePrimitivePreparedAnalyticShape(
    val semantic: GPUDrawSemanticPayload.CorePrimitive,
    val semanticAuthority: GPUCorePrimitivePreparedSemanticAuthority,
    val route: GPUCorePrimitiveDirectNativeRoute.Accepted,
    val uniformBytes: ByteArray,
)

private fun GPUDrawSemanticPayload.CorePrimitive.usesAnalyticShapeUniform80(): Boolean =
    when (geometry) {
        is GPUCorePrimitiveGeometry.Rect -> coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA
        is GPUCorePrimitiveGeometry.RRect ->
            coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor ||
                coverageMode == GPUCorePrimitiveCoverageMode.ScalarAA
        is GPUCorePrimitiveGeometry.TriangulatedPath -> false
    }

private data class GPUCorePrimitivePathStencilPacketPlan(
    val semantic: GPUDrawSemanticPayload.CorePrimitive,
    val scissorBounds: GPUPixelBounds,
)

private fun GPUDrawPacket.hasCorePrimitiveSemanticAuthority(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    capabilities: GPUCapabilities,
): Boolean {
    if (!semantic.hasStructuralIntegrity()) return false
    if (semantic.sourceFamily != GPUCorePrimitiveSourceFamily.Rect) {
        if (semantic.sourceFamily == GPUCorePrimitiveSourceFamily.RRect) {
            return analysisRecordId == semantic.analysisRecordId &&
                semantic.analysisRecordId == "analysis.fill_rrect.$commandIdValue" &&
                semantic.analysisCommandFamily == "FillRRect" &&
                renderStepId.value == CORE_PRIMITIVE_FILL_RRECT_STEP_IDENTITY &&
                semantic.geometry is GPUCorePrimitiveGeometry.RRect
        }
        if (semantic.analysisRecordId != null || semantic.analysisCommandFamily != null ||
            semantic.rectRouteAuthority != null || semantic.rectGeometryAuthority != null ||
            semantic.rrectGeometryAuthority != null
        ) return false
        return when (semantic.sourceFamily) {
            GPUCorePrimitiveSourceFamily.Color -> semantic.geometry is GPUCorePrimitiveGeometry.Rect
            GPUCorePrimitiveSourceFamily.PointLine,
            GPUCorePrimitiveSourceFamily.DRRect,
            GPUCorePrimitiveSourceFamily.Path,
            -> semantic.geometry is GPUCorePrimitiveGeometry.TriangulatedPath
            GPUCorePrimitiveSourceFamily.RRect -> false
            GPUCorePrimitiveSourceFamily.Rect -> false
        }
    }
    if (analysisRecordId != semantic.analysisRecordId ||
        semantic.analysisRecordId != "analysis.fill_rect.$commandIdValue" ||
        semantic.analysisCommandFamily != "FillRect"
    ) return false
    return when (renderStepId.value) {
        CORE_PRIMITIVE_FILL_RECT_STEP_IDENTITY ->
            semantic.rectRouteAuthority == GPUCorePrimitiveRectRouteAuthority.RectAxisAligned &&
                semantic.geometry is GPUCorePrimitiveGeometry.Rect
        CORE_PRIMITIVE_AFFINE_FILL_RECT_STEP_IDENTITY -> {
            val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath ?: return false
            semantic.rectRouteAuthority == GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1 &&
                capabilities.facts.any { fact ->
                fact.name == CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY &&
                    fact.value == "supported" && fact.affectsValidity
            } &&
                geometry.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles &&
                geometry.vertices.size == 8 && geometry.indices == listOf(0, 1, 2, 0, 2, 3) &&
                !geometry.inverseFill && geometry.strokeStyle == null &&
                geometry.sourceVertexCount == 4 && geometry.sourceContourStarts == listOf(0)
        }
        else -> false
    }
}

private fun directCorePrimitiveGeometryBytes(
    packet: GPUDrawPacket,
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    acceptedClipStencilPlan: GPUClipExecutionPlan.StencilCoverage? = null,
    acceptedCoverageMaskPlan: GPUClipExecutionPlan.CoverageMask? = null,
): GPUCorePrimitiveDirectGeometryBytes? {
    if (packet.role != GPUDrawPacketRole.Shading ||
        semantic.coverageMode != GPUCorePrimitiveCoverageMode.FullOrScissor ||
        !packet.blendPlan.isCanonicalSolidRectSrcOver()
    ) return null
    when (packet.clipExecutionPlan) {
        GPUClipExecutionPlan.NoClip,
        is GPUClipExecutionPlan.ScissorOnly,
        is GPUClipExecutionPlan.AnalyticCoverage,
        is GPUClipExecutionPlan.AnalyticIntersection,
        -> Unit
        is GPUClipExecutionPlan.StencilCoverage -> if (
            acceptedClipStencilPlan == null ||
            packet.clipExecutionPlan.canonicalIdentity() != acceptedClipStencilPlan.canonicalIdentity()
        ) return null
        is GPUClipExecutionPlan.CoverageMask -> if (
            acceptedCoverageMaskPlan == null ||
            packet.clipExecutionPlan.canonicalIdentity() != acceptedCoverageMaskPlan.canonicalIdentity()
        ) return null
        else -> return null
    }
    val (vertexCount, indexCount) = when (val geometry = semantic.geometry) {
        is GPUCorePrimitiveGeometry.Rect -> 8 to 6
        is GPUCorePrimitiveGeometry.RRect -> return null
        is GPUCorePrimitiveGeometry.TriangulatedPath -> {
            if (geometry.geometryMode != GPUCorePrimitiveGeometryMode.DirectTriangles ||
                geometry.inverseFill || geometry.strokeStyle != null
            ) return null
            geometry.vertices.size to geometry.indices.size
        }
    }
    return GPUCorePrimitiveDirectGeometryBytes(
        vertexBytes = Math.multiplyExact(vertexCount.toLong(), Float.SIZE_BYTES.toLong()),
        indexBytes = Math.multiplyExact(indexCount.toLong(), Int.SIZE_BYTES.toLong()),
    )
}

internal fun corePrimitiveCoverageMaskProducerUniformBytes(
    plan: GPUClipExecutionPlan.CoverageMask,
    producer: org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan,
): ByteArray {
    val bounds = when (val geometry = producer.geometry) {
        is GPUClipExecutionGeometry.Rect -> geometry.bounds
        is GPUClipExecutionGeometry.RRect -> geometry.bounds
        is GPUClipExecutionGeometry.Path -> error("Coverage-mask eligibility rejects path producers")
    }
    val radii = (producer.geometry as? GPUClipExecutionGeometry.RRect)?.radii ?: List(8) { 0f }
    return ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(plan.bounds.left.toFloat())
        putFloat(plan.bounds.top.toFloat())
        putFloat(plan.bounds.width.toFloat())
        putFloat(plan.bounds.height.toFloat())
        putFloat(bounds.left)
        putFloat(bounds.top)
        putFloat(bounds.right)
        putFloat(bounds.bottom)
        radii.take(4).forEach(::putFloat)
        radii.drop(4).take(4).forEach(::putFloat)
    }.array()
}

internal fun corePrimitiveCoverageMaskConsumerUniformBytes(
    plan: GPUClipExecutionPlan.CoverageMask,
    semantic: GPUDrawSemanticPayload.CorePrimitive,
): ByteArray = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
    putFloat(semantic.targetBounds.width.toFloat())
    putFloat(semantic.targetBounds.height.toFloat())
    putInt(plan.bounds.left)
    putInt(plan.bounds.top)
    putInt(plan.bounds.width)
    putInt(plan.bounds.height)
    putLong(0L)
    semantic.premultipliedRgba.forEach(::putFloat)
    putInt(if (plan.consumer.invert) 1 else 0)
    repeat(12) { put(0) }
}.array()

private fun pathStencilGeometryBytes(
    semantic: GPUDrawSemanticPayload.CorePrimitive,
): GPUCorePrimitiveDirectGeometryBytes? {
    val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath ?: return null
    if (geometry.geometryMode != GPUCorePrimitiveGeometryMode.StencilEdgeFan) return null
    return GPUCorePrimitiveDirectGeometryBytes(
        vertexBytes = Math.addExact(
            Math.multiplyExact(geometry.vertices.size.toLong(), Float.SIZE_BYTES.toLong()),
            4L * 2L * Float.SIZE_BYTES,
        ),
        indexBytes = Math.addExact(
            Math.multiplyExact(geometry.indices.size.toLong(), Int.SIZE_BYTES.toLong()),
            6L * Int.SIZE_BYTES,
        ),
    )
}

private fun pathStencilScissorBounds(
    geometry: GPUCorePrimitiveGeometry.TriangulatedPath,
    clipExecutionPlan: GPUClipExecutionPlan,
    targetBounds: GPUPixelBounds,
): GPUPixelBounds? {
    val clipBounds = when (clipExecutionPlan) {
        GPUClipExecutionPlan.NoClip -> targetBounds
        is GPUClipExecutionPlan.ScissorOnly -> clipExecutionPlan.scissor
        else -> return null
    }
    val coverViewport = if (geometry.inverseFill) targetBounds else geometry.coverBounds
    val left = maxOf(coverViewport.left, clipBounds.left)
    val top = maxOf(coverViewport.top, clipBounds.top)
    val right = minOf(coverViewport.right, clipBounds.right)
    val bottom = minOf(coverViewport.bottom, clipBounds.bottom)
    return if (right <= left || bottom <= top) null else GPUPixelBounds(left, top, right, bottom)
}

private fun corePrimitiveGeometryBufferPreparation(
    resource: GPUFrameBufferRef,
    byteSize: Long,
    role: GPUFrameResourceRole,
    usage: GPUFrameResourceUsage,
    label: String,
): GPUResourcePreparationRequest = GPUResourcePreparationRequest(
    resource = resource,
    descriptor = GPUFrameBufferDescriptor(byteSize, 4L),
    role = role,
    usages = setOf(GPUFrameResourceUsage.CopyDestination, usage),
    lifetime = GPUFrameResourceLifetime.FrameLocal,
    byteSize = byteSize,
    diagnosticLabel = label,
)

internal fun corePrimitiveTargetPreparation(
    target: GPUFrameTargetRef,
    bounds: GPUPixelBounds,
): GPUResourcePreparationRequest = GPUResourcePreparationRequest(
    resource = target,
    descriptor = corePrimitiveTargetDescriptor(bounds),
    role = GPUFrameResourceRole.SceneTarget,
    usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
    lifetime = GPUFrameResourceLifetime.FrameLocal,
    byteSize = corePrimitiveTargetByteSize(bounds),
    diagnosticLabel = "core-primitive.scene-target",
)

internal fun isCanonicalCorePrimitiveTargetPreparation(
    request: GPUResourcePreparationRequest,
    target: GPUFrameTargetRef,
    bounds: GPUPixelBounds,
): Boolean {
    val expected = try {
        corePrimitiveTargetPreparation(target, bounds)
    } catch (_: ArithmeticException) {
        return false
    }
    return request.resource == expected.resource &&
        request.descriptor == expected.descriptor &&
        request.role == expected.role &&
        request.usages == expected.usages &&
        request.lifetime == expected.lifetime &&
        request.byteSize == expected.byteSize
}

internal fun corePrimitiveScissorAuthority(bounds: GPUPixelBounds): String =
    "scissor_${bounds.left.toFloat()}_${bounds.top.toFloat()}_${bounds.right.toFloat()}_${bounds.bottom.toFloat()}"

private fun GPUClipExecutionPlan.contentKeyOrNull(): String? = when (this) {
    is GPUClipExecutionPlan.StencilCoverage -> contentKey
    is GPUClipExecutionPlan.CoverageMask -> contentKey
    GPUClipExecutionPlan.NoClip,
    is GPUClipExecutionPlan.ScissorOnly,
    is GPUClipExecutionPlan.AnalyticCoverage,
    is GPUClipExecutionPlan.AnalyticIntersection,
    is GPUClipExecutionPlan.Refused,
    -> null
}

private fun GPUClipExecutionPlan.clipResourceKey(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(canonicalIdentity().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private data class GPUCoreClipArtifactTopology(
    val contentKey: String,
    val preparations: List<GPUResourcePreparationRequest>,
    val allocations: List<GPUFrameMemoryAllocation>,
    val producerTasks: List<GPUTask.Render>,
    val producerDependencies: List<GPUTaskDependency>,
    val finalProducerId: GPUTaskID,
    val consumerResourceUse: GPUFrameResourceUse,
    val orderingToken: String,
    val atomicGroupId: String?,
)

internal data class GPUCorePrimitiveClipProducerValidation(
    val sealedProducerPacketIds: Set<GPUDrawPacketID>,
    val diagnostic: GPUDiagnostic? = null,
)

internal fun validateCorePrimitiveClipProducerAuthority(
    framePlan: GPUFramePlan,
): GPUCorePrimitiveClipProducerValidation {
    val renders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
    val renderIndices = renders.associateWith(framePlan.steps::indexOf)
    val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
    fun refuse(message: String) = GPUCorePrimitiveClipProducerValidation(
        emptySet(),
        GPUDiagnostic(
            GPUDiagnosticCode("invalid.preflight.core_primitive_clip_producer_authority"),
            GPUDiagnosticDomain.Execution,
            GPUDiagnosticSeverity.Error,
            message,
        ),
    )
    data class ProducerEntry(
        val index: Int,
        val render: GPUFrameStep.RenderPassStep,
        val packet: GPUDrawPacket,
        val plan: GPUClipExecutionPlan,
        val authority: GPUClipProducerAuthority,
    )
    fun consumers(plan: GPUClipExecutionPlan): List<Pair<Int, GPUFrameStep.RenderPassStep>> =
        renders.mapNotNull { render ->
            val matches = render.drawPackets.any { draw ->
                draw.role == GPUDrawPacketRole.Shading &&
                    (draw.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive)
                        ?.clipExecutionPlanIdentity == plan.canonicalIdentity() &&
                    draw.clipExecutionPlan?.canonicalIdentity() == plan.canonicalIdentity()
            }
            if (matches) renderIndices.getValue(render) to render else null
        }
    fun exactDependency(
        from: GPUTaskID,
        to: GPUTaskID,
        token: String,
        reason: String,
        atomicGroup: String?,
    ): Boolean {
        val pairEdges = framePlan.dependencies.filter { dependency ->
            dependency.fromTaskId == from && dependency.toTaskId == to
        }
        val matches = pairEdges.filter { dependency ->
            dependency.fromTaskId == from && dependency.toTaskId == to &&
                dependency.dependencyKind == "clip-producer-consumer" &&
                dependency.useToken?.value == token && dependency.reasonCode == reason &&
                dependency.atomicGroupId?.value == atomicGroup
        }
        return pairEdges.size == 1 && matches.size == 1 && framePlan.dependencies.none { dependency ->
            dependency.fromTaskId == to && dependency.toTaskId == from &&
                dependency.useToken?.value == token
        }
    }
    val entries = mutableListOf<ProducerEntry>()
    for (render in renders) {
        for (packet in render.drawPackets) {
            if (packet.role != GPUDrawPacketRole.StencilProducer &&
                packet.role != GPUDrawPacketRole.ClipProducer
            ) continue
            if (render.drawPackets.size != 1 || render.sourceTaskIds.size != 1 ||
                render.batches.size != 1 || render.batches.single().packets.singleOrNull() != packet ||
                render.batches.single().sourceTaskIds != render.sourceTaskIds
            ) return refuse("Core clip producer must be one sealed packet, task, and batch.")
            val nativeStencilCandidate = packet.corePrimitiveClipStencilPreparedCandidate
                ?.takeIf { packet.role == GPUDrawPacketRole.StencilProducer }
            val nativeCoverageSlabSeal = packet.corePrimitivePreparedAuthority
                ?.coverageMaskUniformSlabSeal
                ?.takeIf { packet.role == GPUDrawPacketRole.ClipProducer }
            val expectedVertexSourceLabel = if (nativeStencilCandidate != null) {
                CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
            } else {
                "clip-producer-authority"
            }
            if (packet.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                packet.renderStepVersion != 1 || packet.vertexSourceLabel != expectedVertexSourceLabel
            ) return refuse("Core clip producer packet authority is stale or incomplete.")
            val plan = packet.clipExecutionPlan
                ?: return refuse("Core clip producer is missing its classified execution plan.")
            val authority = packet.clipProducerAuthority
                ?: return refuse("Core clip producer is missing its typed producer authority.")
            when (packet.role) {
                GPUDrawPacketRole.StencilProducer -> if (
                    plan !is GPUClipExecutionPlan.StencilCoverage ||
                    authority !is GPUClipProducerAuthority.Stencil
                ) return refuse("Stencil producer requires one exact typed stencil plan and authority.")
                GPUDrawPacketRole.ClipProducer -> if (
                    plan !is GPUClipExecutionPlan.CoverageMask ||
                    authority !is GPUClipProducerAuthority.Mask
                ) return refuse("Mask producer requires one exact typed mask plan and authority.")
            }
            if (packet.blendPlan != corePrimitiveClipProducerBlendPlan(authority)) {
                return refuse("Core clip producer blend authority contradicts its typed producer role.")
            }
            val expectedProducerPipelineKey = nativeStencilCandidate?.producerStructuralKey
                ?.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
                ?: nativeCoverageSlabSeal?.producerSlots
                    ?.singleOrNull { it.packetId == packet.packetId }
                    ?.renderPipelineKey
                ?: corePrimitiveClipProducerPipelineKey(plan, authority)
            if (packet.renderPipelineKey != expectedProducerPipelineKey) {
                return refuse("Core clip producer pipeline key contradicts its structural state.")
            }
            when (packet.role) {
                GPUDrawPacketRole.StencilProducer -> {
                    val stencilPlan = plan as GPUClipExecutionPlan.StencilCoverage
                    val stencilAuthority = authority as GPUClipProducerAuthority.Stencil
                    if (stencilAuthority.producer != stencilPlan.producer ||
                        packet.renderStepId.value != "clip.stencil.producer" ||
                        packet.bindingLayoutHash != "layout.clip.stencil.producer.none" ||
                        packet.targetStateHash != "target.clip.stencil.producer.single-sample"
                    ) return refuse("Stencil producer packet fields contradict the classified plan.")
                    if (nativeStencilCandidate != null &&
                        (nativeStencilCandidate.producerPacketId != packet.packetId ||
                            nativeStencilCandidate.producerCommandId != packet.commandIdValue ||
                            nativeStencilCandidate.contentKey != stencilPlan.contentKey ||
                            nativeStencilCandidate.planCanonicalIdentity != stencilPlan.canonicalIdentity() ||
                            nativeStencilCandidate.producerStructuralKey !=
                            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(
                                stencilPlan.producer.fillRule,
                            ))
                    ) return refuse("Native stencil producer candidate contradicts the classified plan.")
                    if (stencilPlan.producer.loadOperation != GPUClipStencilLoadOperation.Clear ||
                        stencilPlan.producer.storeOperation != GPUClipStencilStoreOperation.Store ||
                        stencilPlan.producer.clearValue != 0u ||
                        stencilPlan.consumer.loadOperation != GPUClipStencilLoadOperation.Load ||
                        stencilPlan.consumer.storeOperation != GPUClipStencilStoreOperation.Store ||
                        stencilPlan.consumer.clearValue != null ||
                        stencilPlan.consumer.passOperation != org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation.Keep ||
                        stencilPlan.consumer.failOperation != org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation.Keep ||
                        stencilPlan.consumer.depthFailOperation != org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation.Keep
                    ) return refuse("Stencil artifact requires Clear(0)+Store and read-only Load+Keep consumers.")
                    val targetPreparation = preparations.singleOrNull { request ->
                        request.resource == render.target && request.role == GPUFrameResourceRole.SceneTarget
                    }
                    val targetDescriptor = targetPreparation?.descriptor as? GPUFrameTextureDescriptor
                        ?: return refuse("Stencil producer target is not the canonical scene texture.")
                    val bounds = targetDescriptor.logicalBounds
                    if (!isCanonicalCorePrimitiveTargetPreparation(targetPreparation, render.target, bounds)) {
                        return refuse("Stencil producer target preparation is not canonical.")
                    }
                    if (stencilPlan.bounds.left < bounds.left || stencilPlan.bounds.top < bounds.top ||
                        stencilPlan.bounds.right > bounds.right || stencilPlan.bounds.bottom > bounds.bottom
                    ) return refuse("Stencil work bounds escape the canonical target.")
                    val expectedDepthStencil = GPUDepthStencilLoadStorePlan.WritableStencil(
                        when (stencilPlan.producer.loadOperation) {
                            GPUClipStencilLoadOperation.Clear -> GPUStencilLoadOperation.Clear
                            GPUClipStencilLoadOperation.Load -> GPUStencilLoadOperation.Load
                        },
                        when (stencilPlan.producer.storeOperation) {
                            GPUClipStencilStoreOperation.Store -> GPUStorePlan.Store
                            GPUClipStencilStoreOperation.Discard -> GPUStorePlan.Discard
                        },
                        stencilPlan.producer.clearValue,
                    )
                    if (render.loadStore != GPULoadStorePlan("load", GPUStorePlan.Store) ||
                        render.depthStencilLoadStore != expectedDepthStencil ||
                        render.samplePlan != GPUSamplePlan.SingleSampleFrame || stencilPlan.sampleCount != 1
                    ) return refuse("Stencil producer color/stencil load-store or sample authority contradicts the plan.")
                    val stencilUse = render.resourceUses.singleOrNull { use ->
                        use.role == GPUFrameResourceRole.ClipDepthStencil &&
                            use.usage == GPUFrameResourceUsage.RenderAttachment && use.write &&
                            use.lifetime == GPUFrameResourceLifetime.FrameLocal
                    } ?: return refuse("Stencil producer requires one writable depth/stencil attachment use.")
                    if (nativeStencilCandidate == null) {
                        if (render.resourceUses != listOf(stencilUse)) {
                            return refuse("Generic stencil producer requires only one writable depth/stencil use.")
                        }
                    } else {
                        val vertexUses = render.resourceUses.filter { use ->
                            use.role == GPUFrameResourceRole.VertexData &&
                                use.usage == GPUFrameResourceUsage.Vertex && !use.write &&
                                use.lifetime == GPUFrameResourceLifetime.FrameLocal
                        }
                        val indexUses = render.resourceUses.filter { use ->
                            use.role == GPUFrameResourceRole.IndexData &&
                                use.usage == GPUFrameResourceUsage.Index && !use.write &&
                                use.lifetime == GPUFrameResourceLifetime.FrameLocal
                        }
                        if (render.resourceUses.size != 3 || vertexUses.size != 1 || indexUses.size != 1) {
                            return refuse("Native stencil producer requires exact vertex, index, and writable depth/stencil uses.")
                        }
                    }
                    val stencilPreparation = preparations.singleOrNull { it.resource == stencilUse.resource }
                        ?: return refuse("Stencil producer depth/stencil preparation is missing.")
                    val stencilDescriptor = stencilPreparation.descriptor as? GPUFrameTextureDescriptor
                        ?: return refuse("Stencil producer depth/stencil resource is not a texture.")
                    val expectedBytes = try {
                        corePrimitiveDepthStencilByteSize(bounds, stencilPlan.sampleCount)
                    } catch (_: ArithmeticException) {
                        return refuse("Stencil producer depth/stencil byte size overflowed.")
                    }
                    if (stencilPreparation.role != GPUFrameResourceRole.ClipDepthStencil ||
                        stencilPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
                        stencilPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                        stencilDescriptor.logicalBounds != bounds ||
                        stencilDescriptor.format.value != "depth24plus-stencil8" ||
                        stencilDescriptor.sampleCount != stencilPlan.sampleCount ||
                        stencilPreparation.byteSize != expectedBytes
                    ) return refuse("Stencil producer depth/stencil dimensions, format, samples, or bytes mismatch.")
                    val stencilConsumers = consumers(stencilPlan)
                    if (stencilConsumers.isEmpty() || stencilConsumers.any { (_, consumer) ->
                            val exactUses = consumer.resourceUses.filter {
                                it.role == GPUFrameResourceRole.ClipDepthStencil
                            }
                            consumer.target != render.target || exactUses.size != 1 ||
                                exactUses.single() != stencilUse.copy(write = false) ||
                                consumer.depthStencilLoadStore != GPUDepthStencilLoadStorePlan.ReadOnlyKeep ||
                                consumer.resourceUses.any { it.role == GPUFrameResourceRole.ClipMask } ||
                                consumer.samplePlan != GPUSamplePlan.SingleSampleFrame
                        }
                    ) return refuse("Stencil producer and consumers do not share one exact attachment authority.")
                }
                GPUDrawPacketRole.ClipProducer -> {
                    val maskPlan = plan as GPUClipExecutionPlan.CoverageMask
                    val maskAuthority = authority as GPUClipProducerAuthority.Mask
                    val nativeCoverageSlot = nativeCoverageSlabSeal?.producerSlots
                        ?.singleOrNull { it.packetId == packet.packetId }
                    if (maskAuthority.producer !in maskPlan.producers ||
                        packet.renderStepId.value != "clip.mask.producer" ||
                        packet.bindingLayoutHash != (nativeCoverageSlot?.bindingLayoutHash
                            ?: "layout.clip.mask.producer.none") ||
                        packet.targetStateHash != "target.clip.mask.producer.single-sample"
                    ) return refuse("Mask producer packet fields contradict the classified plan.")
                    if (maskPlan.depthStencilRequired || maskPlan.sampleCount != 1 ||
                        render.samplePlan != GPUSamplePlan.SingleSampleFrame || render.depthStencilLoadStore != null ||
                        render.resourceUses.any { it.role == GPUFrameResourceRole.ClipDepthStencil }
                    ) return refuse("Mask producer requires the B3.0 single-sample color-only topology.")
                    val maskUse = render.resourceUses.singleOrNull { use ->
                        use.resource == render.target && use.role == GPUFrameResourceRole.ClipMask &&
                            use.usage == GPUFrameResourceUsage.RenderAttachment && use.write &&
                            use.lifetime == GPUFrameResourceLifetime.FrameLocal
                    } ?: return refuse("Mask producer requires one writable mask target use.")
                    val uniformUses = render.resourceUses.filter { use ->
                        use.role == GPUFrameResourceRole.UniformData &&
                            use.usage == GPUFrameResourceUsage.Uniform && !use.write &&
                            use.lifetime == GPUFrameResourceLifetime.FrameLocal
                    }
                    if (nativeCoverageSlot == null) {
                        if (render.resourceUses != listOf(maskUse)) {
                            return refuse("Generic mask producer requires only one writable mask use.")
                        }
                    } else if (render.resourceUses.size != 2 || uniformUses.size != 1 ||
                        nativeCoverageSlabSeal.maskResource != maskUse.resource ||
                        nativeCoverageSlabSeal.planCanonicalIdentity != maskPlan.canonicalIdentity() ||
                        nativeCoverageSlot.commandId != packet.commandIdValue ||
                        nativeCoverageSlot.structuralPipelineKey !=
                        packet.corePrimitivePreparedAuthority?.structuralPipelineKey
                    ) {
                        return refuse("Prepared mask producer uniform64 authority was substituted.")
                    }
                    val maskPreparation = preparations.singleOrNull { request ->
                        request.resource == maskUse.resource && request.role == GPUFrameResourceRole.ClipMask
                    }
                    val maskDescriptor = maskPreparation?.descriptor as? GPUFrameTextureDescriptor
                        ?: return refuse("Mask producer target preparation is missing.")
                    if (maskPreparation.usages != setOf(
                            GPUFrameResourceUsage.RenderAttachment,
                            GPUFrameResourceUsage.TextureBinding,
                        ) || maskPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                        maskPreparation.byteSize != maskPlan.resolvedBytes ||
                        maskDescriptor.logicalBounds != maskPlan.bounds || maskDescriptor.sampleCount != 1 ||
                        maskDescriptor.format.value != "rgba8unorm"
                    ) return refuse("Mask producer preparation dimensions, format, usages, lifetime, samples, or bytes mismatch.")
                }
            }
            entries += ProducerEntry(renderIndices.getValue(render), render, packet, plan, authority)
        }
    }
    entries.filter { it.plan is GPUClipExecutionPlan.StencilCoverage }
        .groupBy { it.plan.canonicalIdentity() }
        .forEach { (_, stencilEntries) ->
            if (stencilEntries.size != 1) return refuse("Stencil plans require one unique producer.")
            val entry = stencilEntries.single()
            val plan = entry.plan as GPUClipExecutionPlan.StencilCoverage
            val consumers = consumers(plan)
            val producerTask = entry.render.sourceTaskIds.single()
            val depthStencil = entry.render.resourceUses.single {
                it.role == GPUFrameResourceRole.ClipDepthStencil
            }.resource
            consumers.forEach { (consumerIndex, consumer) ->
                if (entry.index >= consumerIndex) return refuse("Stencil producer must precede every consumer step.")
                consumer.sourceTaskIds.forEach { consumerTask ->
                    if (!exactDependency(
                            producerTask,
                            consumerTask,
                            plan.orderingToken.value,
                            "preserve.core-primitive.clip.producer-before-consumer",
                            plan.atomicGroup.value,
                        )
                    ) return refuse("Stencil producer-consumer dependency authority is missing or inverted.")
                }
                if (renders.any { candidate ->
                        val index = renderIndices.getValue(candidate)
                        index in (entry.index + 1)..consumerIndex && candidate !== consumer &&
                            candidate.resourceUses.any { it.resource == depthStencil && it.write }
                    }
                ) return refuse("A foreign depth/stencil write splits one stencil atomic group.")
            }
        }
    entries.filter { it.plan is GPUClipExecutionPlan.CoverageMask }
        .groupBy { it.plan.canonicalIdentity() }
        .forEach { (_, maskEntries) ->
            val plan = maskEntries.first().plan as GPUClipExecutionPlan.CoverageMask
            val ordered = maskEntries.sortedBy(ProducerEntry::index)
            val authorities = ordered.map { (it.authority as GPUClipProducerAuthority.Mask).producer }
            if (authorities != plan.producers) {
                return refuse("Mask producers must retain exact strict source order.")
            }
            if (ordered.map { it.render.target }.distinct().size != 1 ||
                ordered.map { entry ->
                    entry.render.resourceUses.single { it.role == GPUFrameResourceRole.ClipMask }.resource
                }.distinct().size != 1
            ) return refuse("Mask producers must share one exact target and writable mask resource.")
            ordered.forEachIndexed { index, entry ->
                val expected = GPULoadStorePlan(
                    loadOp = if (index == 0) "clear" else "load",
                    storePlan = GPUStorePlan.Store,
                    clearColorLabel = if (index == 0) CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL else null,
                )
                if (entry.render.loadStore != expected) {
                    return refuse("Mask producer color load/store contradicts its source order.")
                }
            }
            ordered.zipWithNext().forEachIndexed { index, (from, to) ->
                if (!exactDependency(
                        from.render.sourceTaskIds.single(),
                        to.render.sourceTaskIds.single(),
                        plan.orderingToken.value,
                        "preserve.core-primitive.clip.mask-producer.$index",
                        null,
                    )
                ) return refuse("Mask producer chain dependency authority is missing or inverted.")
            }
            val final = ordered.last()
            val maskResource = final.render.target
            val maskConsumers = consumers(plan)
            if (maskConsumers.isEmpty()) return refuse("Mask producer has no exact consumer.")
            val nativeCoverageSeals = ordered.mapNotNull { entry ->
                entry.packet.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal
            }
            val nativeCoverageSeal = nativeCoverageSeals.firstOrNull()?.takeIf { seal ->
                nativeCoverageSeals.size == ordered.size && nativeCoverageSeals.all { it === seal }
            }
            maskConsumers.forEachIndexed { maskConsumerIndex, (consumerIndex, consumer) ->
                val uses = consumer.resourceUses.filter { it.role == GPUFrameResourceRole.ClipMask }
                val expectedUse = GPUFrameResourceUse(
                    maskResource,
                    GPUFrameResourceRole.ClipMask,
                    GPUFrameResourceUsage.TextureBinding,
                    GPUFrameResourceLifetime.FrameLocal,
                    false,
                )
                if (final.index >= consumerIndex || uses.size != 1 || uses.single() != expectedUse ||
                    consumer.resourceUses.any { it.role == GPUFrameResourceRole.ClipDepthStencil } ||
                    consumer.depthStencilLoadStore != null
                ) return refuse("Mask consumer binding or producer-before-consumer order is invalid.")
                if (nativeCoverageSeal == null || maskConsumerIndex == 0) {
                    consumer.sourceTaskIds.forEach { consumerTask ->
                    if (!exactDependency(
                            final.render.sourceTaskIds.single(),
                            consumerTask,
                            plan.orderingToken.value,
                            "preserve.core-primitive.clip.producer-before-consumer",
                            null,
                        )
                    ) return refuse("Mask producer-consumer dependency authority is missing or inverted.")
                    }
                }
            }
            if (nativeCoverageSeal != null) {
                if (nativeCoverageSeal.producerSourceOrders != plan.producers.map { it.sourceOrder } ||
                    nativeCoverageSeal.producerPacketIds != ordered.map { it.packet.packetId } ||
                    nativeCoverageSeal.consumerPacketIds != maskConsumers.map {
                        it.second.drawPackets.single().packetId
                    } || nativeCoverageSeal.consumerCommandIds != maskConsumers.map {
                        it.second.drawPackets.single().commandIdValue
                    }
                ) return refuse("Prepared mask slab packet order was substituted.")
                maskConsumers.zipWithNext().forEachIndexed { index, (left, right) ->
                    val fromTask = left.second.sourceTaskIds.single()
                    val toTask = right.second.sourceTaskIds.single()
                    val expectedToken = nativeCoverageSeal.consumerSlots[index + 1]
                        .dependencyFromPreviousConsumerToken
                    val pairEdges = framePlan.dependencies.filter {
                        it.fromTaskId == fromTask && it.toTaskId == toTask
                    }
                    if (pairEdges.size != 1 ||
                        pairEdges.single().dependencyKind != "prepared-scene-order" ||
                        pairEdges.single().useToken?.value != expectedToken ||
                        pairEdges.single().reasonCode != "preserve.prepared-scene.order" ||
                        framePlan.dependencies.any {
                            it.fromTaskId == toTask && it.toTaskId == fromTask
                        }
                    ) return refuse("Prepared mask consumer order dependency was substituted.")
                }
            }
        }
    val resourceConsumerPlans = renders.flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        .filter { it.role == GPUDrawPacketRole.Shading }
        .mapNotNull(GPUDrawPacket::clipExecutionPlan)
        .filter { it is GPUClipExecutionPlan.StencilCoverage || it is GPUClipExecutionPlan.CoverageMask }
    if (resourceConsumerPlans.any { consumerPlan ->
            entries.none { it.plan.canonicalIdentity() == consumerPlan.canonicalIdentity() }
        }
    ) return refuse("Resource-backed clip consumer is missing its sealed producer topology.")
    return GPUCorePrimitiveClipProducerValidation(entries.map { it.packet.packetId }.toSet())
}

data class GPUCorePrimitivePreparedFrameRequest(
    val baseTaskList: GPUTaskList,
    val capabilities: GPUCapabilities,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
    val readbackRequestId: GPUReadbackRequestID? = null,
    val configuredAggregateBudgetBytes: Long = 1L shl 30,
)

sealed interface GPUCorePrimitivePreparedFrameResult {
    data class Recorded(val taskList: GPUTaskList) : GPUCorePrimitivePreparedFrameResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUCorePrimitivePreparedFrameResult
}

/** Adds the canonical target/readback envelope without re-planning blend, geometry, or clip routing. */
class GPUCorePrimitivePreparedFrameTaskListBuilder(
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    fun build(request: GPUCorePrimitivePreparedFrameRequest): GPUCorePrimitivePreparedFrameResult {
        request.baseTaskList.tasks.filterIsInstance<GPUTask.Refused>().firstOrNull()?.let {
            return GPUCorePrimitivePreparedFrameResult.Refused(it.diagnostic)
        }
        request.baseTaskList.diagnostics.firstOrNull(GPUDiagnostic::isTerminal)?.let {
            return GPUCorePrimitivePreparedFrameResult.Refused(it)
        }
        if (request.targetBounds.left != 0 || request.targetBounds.top != 0 ||
            request.targetBounds.width <= 0 || request.targetBounds.height <= 0
        ) {
            return refused(
                "unsupported.recording.core_primitive_target",
                "Prepared core primitive recording requires one non-empty zero-origin target.",
            )
        }
        if (request.configuredAggregateBudgetBytes <= 0L) {
            return refused(
                "invalid.recording.core_primitive_budget",
                "Core primitive aggregate budget must be positive.",
            )
        }
        val baseRenders = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        if (baseRenders.isEmpty() || request.baseTaskList.tasks.any { it !is GPUTask.Render }) {
            return refused(
                "unsupported.recording.core_primitive_base_tasks",
                "Prepared core primitives require an accepted render-only base task list.",
            )
        }
        val basePackets = baseRenders.flatMap(GPUTask.Render::drawPackets)
        if (basePackets.map(GPUDrawPacket::commandIdValue).distinct().size != basePackets.size ||
            basePackets.map(GPUDrawPacket::commandIdValue).toSet() != request.semanticsByCommandId.keys ||
            basePackets.any { it.clipCoveragePlan == null }
        ) {
            return refused(
                "invalid.recording.core_primitive_semantics",
                "Every accepted base packet requires exactly one gathered semantic payload and clip plan.",
            )
        }
        basePackets.firstOrNull { packet ->
            !packet.hasCorePrimitiveSemanticAuthority(
                requireNotNull(request.semanticsByCommandId[packet.commandIdValue]),
                request.capabilities,
            )
        }?.let {
            return refused(
                "invalid.recording.core_primitive_semantic_authority",
                "Core primitive semantic source family and geometry must match the analyzed packet route.",
            )
        }
        for (render in baseRenders) {
            for (packet in render.drawPackets) {
                val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
                when (
                    val authority = validateCorePrimitiveCoverageSampleAuthority(
                        geometry = semantic.geometry,
                        coverageMode = semantic.coverageMode,
                        targetBounds = semantic.targetBounds,
                        samplePlan = render.samplePlan,
                        capabilities = request.capabilities,
                    )
                ) {
                    GPUCorePrimitiveCoverageSampleAuthority.Accepted -> Unit
                    is GPUCorePrimitiveCoverageSampleAuthority.Refused ->
                        return refused(authority.code, authority.message)
                }
            }
        }
        if (baseRenders.any { it.samplePlan != GPUSamplePlan.SingleSampleFrame }) {
            return refused(
                "unsupported.recording.core_primitive_base_sample_plan",
                "Prepared core primitives cannot replace a non-single-sample base render authority.",
            )
        }
        if (basePackets.any { it.clipExecutionPlan == null }) {
            return refused(
                "invalid.recording.core_primitive_clip_execution_plan_missing",
                "Every core primitive packet requires one classified clip execution plan.",
            )
        }
        basePackets.mapNotNull(GPUDrawPacket::clipExecutionPlan)
            .filterIsInstance<GPUClipExecutionPlan.Refused>()
            .firstOrNull()
            ?.let { return refused(it.code, it.message) }
        val analyticClipAuthoritiesByCommandId = linkedMapOf<
            Int,
            GPUCorePrimitiveAnalyticClipAuthority.Accepted,
        >()
        basePackets.forEach { packet ->
            val plan = packet.clipExecutionPlan as? GPUClipExecutionPlan.AnalyticCoverage
                ?: return@forEach
            when (val authority = corePrimitiveAnalyticClipAuthority(plan, request.targetBounds)) {
                is GPUCorePrimitiveAnalyticClipAuthority.Accepted ->
                    analyticClipAuthoritiesByCommandId[packet.commandIdValue] = authority
                is GPUCorePrimitiveAnalyticClipAuthority.Refused ->
                    return refused(authority.code, authority.message)
            }
        }
        val analyticIntersectionAuthoritiesByCommandId = linkedMapOf<
            Int,
            GPUCorePrimitiveAnalyticIntersectionAuthority.Accepted,
        >()
        basePackets.forEach { packet ->
            val plan = packet.clipExecutionPlan as? GPUClipExecutionPlan.AnalyticIntersection
                ?: return@forEach
            when (val authority = corePrimitiveAnalyticIntersectionAuthority(plan, request.targetBounds)) {
                is GPUCorePrimitiveAnalyticIntersectionAuthority.Accepted ->
                    analyticIntersectionAuthoritiesByCommandId[packet.commandIdValue] = authority
                is GPUCorePrimitiveAnalyticIntersectionAuthority.Refused ->
                    return refused(authority.code, authority.message)
            }
        }
        val analyticShapeCommandIds = basePackets.mapNotNullTo(linkedSetOf()) { packet ->
            packet.commandIdValue.takeIf {
                request.semanticsByCommandId.getValue(it).usesAnalyticShapeUniform80()
            }
        }
        if (analyticShapeCommandIds.any {
                it in analyticClipAuthoritiesByCommandId ||
                    it in analyticIntersectionAuthoritiesByCommandId
            }
        ) {
            return refused(
                "unsupported.recording.core_primitive_mixed_uniform_layouts",
                "One direct CorePrimitive draw cannot combine analytic-shape uniform80 with analytic-clip " +
                    "uniform64 or uniform160.",
            )
        }
        val preparedAnalyticShapesByCommandId = linkedMapOf<Int, GPUCorePrimitivePreparedAnalyticShape>()
        for (render in baseRenders) {
            for (packet in render.drawPackets) {
                val commandId = packet.commandIdValue
                if (commandId !in analyticShapeCommandIds) continue
                val clipExecutionPlan = requireNotNull(packet.clipExecutionPlan)
                if (clipExecutionPlan != GPUClipExecutionPlan.NoClip &&
                    clipExecutionPlan !is GPUClipExecutionPlan.ScissorOnly
                ) {
                    return refused(
                        "unsupported.recording.core_primitive_analytic_shape_clip",
                        "Prepared analytic shapes currently require NoClip or ScissorOnly execution.",
                    )
                }
                val preparedSemantic = request.semanticsByCommandId.getValue(commandId)
                    .withClipExecutionPlanIdentity(clipExecutionPlan.canonicalIdentity())
                val semanticAuthority = GPUCorePrimitivePreparedSemanticAuthority.capture(preparedSemantic)
                val uniformBytes = when (val uniform = buildCorePrimitiveAnalyticShapeUniform(
                    preparedSemantic,
                    semanticAuthority,
                )) {
                    is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted ->
                        uniform.block.packedBytes()
                    is GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused ->
                        return refused(uniform.code, uniform.message)
                }
                val route = when (val decision = classifyCorePrimitiveDirectNativeRoute(
                    semantic = preparedSemantic,
                    clipExecutionPlan = clipExecutionPlan,
                    blendPlan = packet.blendPlan,
                    samplePlan = render.samplePlan,
                    targetFormat = "rgba8unorm",
                )) {
                    is GPUCorePrimitiveDirectNativeRoute.Accepted -> decision
                    is GPUCorePrimitiveDirectNativeRoute.Refused ->
                        return refused(decision.code, decision.message)
                }
                preparedAnalyticShapesByCommandId[commandId] = GPUCorePrimitivePreparedAnalyticShape(
                    semantic = preparedSemantic,
                    semanticAuthority = semanticAuthority,
                    route = route,
                    uniformBytes = uniformBytes,
                )
            }
        }
        basePackets.mapNotNull(GPUDrawPacket::clipExecutionPlan)
            .filterIsInstance<GPUClipExecutionPlan.StencilCoverage>()
            .firstOrNull { plan ->
                plan.producer.loadOperation != GPUClipStencilLoadOperation.Clear ||
                    plan.producer.storeOperation != GPUClipStencilStoreOperation.Store ||
                    plan.producer.clearValue != 0u ||
                    plan.consumer.loadOperation != GPUClipStencilLoadOperation.Load ||
                    plan.consumer.storeOperation != GPUClipStencilStoreOperation.Store ||
                    plan.consumer.clearValue != null ||
                    plan.consumer.passOperation != org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation.Keep ||
                    plan.consumer.failOperation != org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation.Keep ||
                    plan.consumer.depthFailOperation != org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation.Keep
            }
            ?.let {
                return refused(
                    "unsupported.recording.core_primitive_clip_stencil_artifact_state",
                    "Frame-local stencil artifacts require Clear(0)+Store and read-only Load+Keep consumers.",
                )
            }
        if (basePackets.mapNotNull(GPUDrawPacket::clipExecutionPlan)
                .filterIsInstance<GPUClipExecutionPlan.CoverageMask>()
                .any(GPUClipExecutionPlan.CoverageMask::depthStencilRequired)
        ) {
            return refused(
                "unsupported.recording.core_primitive_clip_mask_depth_stencil_topology_unavailable",
                "Coverage-mask depth/stencil requires the B3.4 full-target attachment topology.",
            )
        }
        val pathStencilPlansByCommandId = linkedMapOf<Int, GPUCorePrimitivePathStencilPacketPlan>()
        basePackets.forEach { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
            val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath
                ?: return@forEach
            when (geometry.geometryMode) {
                GPUCorePrimitiveGeometryMode.DirectTriangles -> Unit
                GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan -> return refused(
                    "unsupported.recording.core_primitive_path_stroke_stencil",
                    "Prepared path stencil topology does not yet accept stroke edge fans.",
                )
                GPUCorePrimitiveGeometryMode.StencilEdgeFan -> {
                    if (packet.role != GPUDrawPacketRole.Shading ||
                        semantic.coverageMode != GPUCorePrimitiveCoverageMode.Stencil1x
                    ) {
                        return refused(
                            "unsupported.recording.core_primitive_path_stencil_coverage",
                            "Prepared fill path stencil topology requires one Shading packet with Stencil1x coverage.",
                        )
                    }
                    val clipExecutionPlan = requireNotNull(packet.clipExecutionPlan)
                    if (clipExecutionPlan != GPUClipExecutionPlan.NoClip &&
                        clipExecutionPlan !is GPUClipExecutionPlan.ScissorOnly
                    ) {
                        return refused(
                            "unsupported.recording.core_primitive_path_stencil_clip",
                            "Prepared path stencil topology accepts only NoClip or ScissorOnly execution.",
                        )
                    }
                    val scissorBounds = pathStencilScissorBounds(
                        geometry,
                        clipExecutionPlan,
                        request.targetBounds,
                    ) ?: return refused(
                        "unsupported.recording.core_primitive_path_stencil_scissor",
                        "Prepared path stencil geometry and its classified scissor must overlap.",
                    )
                    pathStencilPlansByCommandId[packet.commandIdValue] =
                        GPUCorePrimitivePathStencilPacketPlan(semantic, scissorBounds)
                }
            }
        }
        if ((analyticClipAuthoritiesByCommandId.isNotEmpty() ||
                analyticIntersectionAuthoritiesByCommandId.isNotEmpty()) &&
            pathStencilPlansByCommandId.isNotEmpty()
        ) {
            return refused(
                "unsupported.recording.core_primitive_analytic_clip_path_mix",
                "Prepared analytic direct clips cannot share the current path-stencil attachment topology.",
            )
        }
        val clipArtifacts = linkedMapOf<String, GPUClipExecutionPlan>()
        basePackets.forEach { packet ->
            val plan = requireNotNull(packet.clipExecutionPlan)
            val contentKey = plan.contentKeyOrNull() ?: return@forEach
            val previous = clipArtifacts[contentKey]
            if (previous != null && previous.canonicalIdentity() != plan.canonicalIdentity()) {
                return refused(
                    "invalid.recording.core_primitive_clip_content_key_collision",
                    "One clip content key identifies different full execution plans.",
                )
            }
            clipArtifacts.putIfAbsent(contentKey, plan)
        }
        val limits = request.capabilities.limits ?: return refused(
            "unsupported.recording.core_primitive_limits_unavailable",
            "Prepared core primitive recording requires observed device limits.",
        )
        val staticCoverageMaskPlan = clipArtifacts.values
            .filterIsInstance<GPUClipExecutionPlan.CoverageMask>()
            .singleOrNull()
            ?.takeIf { clipArtifacts.size == 1 }
        val staticCoverageMaskConsumers = staticCoverageMaskPlan?.let { maskPlan ->
            basePackets.filter { packet ->
                packet.clipExecutionPlan?.canonicalIdentity() == maskPlan.canonicalIdentity()
            }
        }.orEmpty()
        val preparedCoverageMaskSemanticsByCommandId = staticCoverageMaskPlan?.let { maskPlan ->
            staticCoverageMaskConsumers.associate { packet ->
                packet.commandIdValue to request.semanticsByCommandId
                    .getValue(packet.commandIdValue)
                    .withClipExecutionPlanIdentity(maskPlan.canonicalIdentity())
            }
        }.orEmpty()
        val coverageMaskPreparedCandidate = staticCoverageMaskPlan?.takeIf {
            staticCoverageMaskConsumers.size == basePackets.size
        }?.let { maskPlan ->
            val key = maskPlan.clipResourceKey()
            val decision = snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate(
                GPUCorePrimitiveCoverageMaskPreparedRouteRequest(
                    plan = maskPlan,
                    consumers = staticCoverageMaskConsumers.map { packet ->
                        val semantic = preparedCoverageMaskSemanticsByCommandId
                            .getValue(packet.commandIdValue)
                        GPUCorePrimitiveCoverageMaskConsumerInput(
                            packetId = packet.packetId,
                            commandId = packet.commandIdValue,
                            sourceOrder = packet.originalPaintOrder,
                            semanticAuthority = GPUCorePrimitivePreparedSemanticAuthority.capture(semantic),
                            coverageMode = semantic.coverageMode,
                            blendPlan = requireNotNull(packet.blendPlan),
                            orderingToken = maskPlan.orderingToken,
                            packetRole = packet.role,
                            geometry = semantic.geometry,
                        )
                    },
                    attachment = GPUCorePrimitiveCoverageMaskAttachmentAuthority(
                        logicalReference = "target.core-primitive.clip-mask.$key",
                        width = request.targetBounds.width,
                        height = request.targetBounds.height,
                        format = GPUCorePrimitiveCoverageMaskAttachmentFormat.Rgba8Unorm,
                        sampleCount = 1,
                        deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration,
                        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                    ),
                ),
            )
            (decision as? GPUCorePrimitiveCoverageMaskPreparedCandidateDecision.Accepted)?.candidate
        }
        val nativeCoverageMaskPlan = staticCoverageMaskPlan?.takeIf {
            coverageMaskPreparedCandidate != null
        }
        val targetBytes = try {
            corePrimitiveTargetByteSize(request.targetBounds)
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.core_primitive_target_size",
                "Core primitive target byte size exceeds signed 64-bit arithmetic.",
            )
        }
        val readbackRequest = request.readbackRequestId?.let { requestId ->
            GPUFrameReadbackRequest(
                requestId,
                request.targetBounds,
                GPUReadbackPixelFormat.Rgba8Unorm,
                GPUColorInterpretation("srgb-premul"),
            )
        }
        val readbackPlan = readbackRequest?.let { frameReadback ->
            when (val plan = readbackLayoutPlanner.plan(frameReadback, request.capabilities)) {
                is GPUReadbackLayoutPlan.Planned -> plan
                is GPUReadbackLayoutPlan.Refused -> return GPUCorePrimitivePreparedFrameResult.Refused(plan.diagnostic)
            }
        }
        val staging = readbackPlan?.let {
            GPUFrameBufferRef("buffer.core-primitive.readback.${request.baseTaskList.frameId.value}")
        }
        val unsupportedMultisampleClip = clipArtifacts.values.firstOrNull { plan ->
            when (plan) {
                is GPUClipExecutionPlan.StencilCoverage -> plan.sampleCount != 1
                is GPUClipExecutionPlan.CoverageMask -> plan.sampleCount != 1
                else -> false
            }
        }
        if (unsupportedMultisampleClip != null) {
            return refused(
                "unsupported.recording.core_primitive_clip_multisample_topology",
                "Core primitive clip producer topology currently requires single-sample plans.",
            )
        }
        val staticNativeClipStencilPlans = clipArtifacts.values
            .filterIsInstance<GPUClipExecutionPlan.StencilCoverage>()
            .filter { it.corePrimitiveClipStencilNativePathOrNull() != null }
        if (staticNativeClipStencilPlans.size > 1 ||
            staticNativeClipStencilPlans.isNotEmpty() && clipArtifacts.size > 1
        ) {
            return refused(
                "unsupported.recording.core_primitive_clip_stencil_multiple_native_artifacts",
                "The bounded native clip-stencil candidate accepts exactly one path artifact.",
            )
        }
        val staticNativeClipStencilPlan = staticNativeClipStencilPlans.singleOrNull()
        val staticNativeClipStencilConsumers = staticNativeClipStencilPlan?.let { candidate ->
            basePackets.filter { packet ->
                packet.clipExecutionPlan?.canonicalIdentity() == candidate.canonicalIdentity()
            }
        }.orEmpty()
        val nativeClipStencilConsumerGeometryBytesByCommandId = try {
            staticNativeClipStencilPlan?.let { candidate ->
                staticNativeClipStencilConsumers.mapNotNull { packet ->
                    directCorePrimitiveGeometryBytes(
                        packet,
                        request.semanticsByCommandId.getValue(packet.commandIdValue),
                        acceptedClipStencilPlan = candidate,
                    )?.let { packet.commandIdValue to it }
                }.toMap().takeIf { it.size == staticNativeClipStencilConsumers.size }
            }.orEmpty()
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.core_primitive_geometry_size",
                "Core primitive clip-stencil consumer geometry byte size exceeds signed 64-bit arithmetic.",
            )
        }
        val nativeClipStencilPlan = staticNativeClipStencilPlan?.takeIf {
            staticNativeClipStencilConsumers.isNotEmpty() &&
                nativeClipStencilConsumerGeometryBytesByCommandId.size ==
                staticNativeClipStencilConsumers.size
        }
        if (nativeClipStencilPlan != null && staticNativeClipStencilConsumers.size != basePackets.size) {
            return refused(
                "unsupported.recording.core_primitive_clip_stencil_mixed_geometry",
                "The bounded clip-stencil arena cannot share slabs with foreign geometry.",
            )
        }
        val nativeClipStencilProducerNdcVertices = nativeClipStencilPlan?.let { candidate ->
            val path = requireNotNull(candidate.corePrimitiveClipStencilNativePathOrNull())
            corePrimitiveClipStencilNdcVertices(
                path.vertices,
                request.targetBounds.width,
                request.targetBounds.height,
            ) ?: return refused(
                "invalid.recording.core_primitive_clip_stencil_producer_geometry",
                "Clip-stencil producer geometry cannot be converted to finite NDC coordinates.",
            )
        }
        val nativeClipStencilProducerFan = nativeClipStencilPlan?.let { candidate ->
            val path = requireNotNull(candidate.corePrimitiveClipStencilNativePathOrNull())
            try {
                corePrimitiveClipStencilEdgeFan(
                    requireNotNull(nativeClipStencilProducerNdcVertices),
                    path.contourStarts,
                )
            } catch (_: IllegalArgumentException) {
                return refused(
                    "invalid.recording.core_primitive_clip_stencil_producer_geometry",
                    "Clip-stencil producer geometry cannot form the exact edge fan.",
                )
            }
        }
        val directGeometryBytesByCommandId = try {
            basePackets.mapNotNull { packet ->
                val analyticShape = preparedAnalyticShapesByCommandId[packet.commandIdValue]
                val bytes = if (analyticShape == null) {
                    directCorePrimitiveGeometryBytes(
                        packet,
                        requireNotNull(request.semanticsByCommandId[packet.commandIdValue]),
                        acceptedCoverageMaskPlan = nativeCoverageMaskPlan,
                    )
                } else {
                    GPUCorePrimitiveDirectGeometryBytes(
                        vertexBytes = Math.multiplyExact(
                            Math.multiplyExact(analyticShape.route.vertexCount.toLong(), 2L),
                            Float.SIZE_BYTES.toLong(),
                        ),
                        indexBytes = Math.multiplyExact(
                            analyticShape.route.indexCount.toLong(),
                            Int.SIZE_BYTES.toLong(),
                        ),
                    )
                }
                bytes?.let { packet.commandIdValue to it }
            }.toMap()
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.core_primitive_geometry_size",
                "Core primitive direct geometry byte size exceeds signed 64-bit arithmetic.",
            )
        }
        analyticClipAuthoritiesByCommandId.keys.firstOrNull { it !in directGeometryBytesByCommandId }
            ?.let {
                return refused(
                    "unsupported.recording.core_primitive_analytic_clip_non_direct_geometry",
                    "Prepared analytic clips require one direct CorePrimitive shading geometry.",
                )
            }
        analyticIntersectionAuthoritiesByCommandId.keys.firstOrNull { it !in directGeometryBytesByCommandId }
            ?.let {
                return refused(
                    "unsupported.recording.core_primitive_analytic_intersection_non_direct_geometry",
                    "Prepared analytic intersections require one direct CorePrimitive shading geometry.",
                )
        }
        val geometryBytesByCommandId = try {
            directGeometryBytesByCommandId + nativeClipStencilConsumerGeometryBytesByCommandId +
                pathStencilPlansByCommandId.mapValues { (_, plan) ->
                    requireNotNull(pathStencilGeometryBytes(plan.semantic))
                }
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.core_primitive_geometry_size",
                "Core primitive path stencil geometry byte size exceeds signed 64-bit arithmetic.",
            )
        }
        val geometryVertexBytes = try {
            geometryBytesByCommandId.values.fold(
                nativeClipStencilProducerFan?.vertices?.size?.let { count ->
                    Math.multiplyExact(count.toLong(), Float.SIZE_BYTES.toLong())
                } ?: 0L,
            ) { total, geometry ->
                Math.addExact(total, geometry.vertexBytes)
            }
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.core_primitive_geometry_size",
                "Core primitive shared vertex byte size exceeds signed 64-bit arithmetic.",
            )
        }
        val geometryIndexBytes = try {
            geometryBytesByCommandId.values.fold(
                nativeClipStencilProducerFan?.indices?.size?.let { count ->
                    Math.multiplyExact(count.toLong(), Int.SIZE_BYTES.toLong())
                } ?: 0L,
            ) { total, geometry ->
                Math.addExact(total, geometry.indexBytes)
            }
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.core_primitive_geometry_size",
                "Core primitive shared index byte size exceeds signed 64-bit arithmetic.",
            )
        }
        val geometryPackets = basePackets.filter { it.commandIdValue in geometryBytesByCommandId }
        val analyticShapeUniformPackets = geometryPackets.filter {
            it.commandIdValue in preparedAnalyticShapesByCommandId
        }
        val analyticUniformPackets = geometryPackets.filter {
            it.commandIdValue in analyticClipAuthoritiesByCommandId
        }
        val analyticIntersectionUniformPackets = geometryPackets.filter {
            it.commandIdValue in analyticIntersectionAuthoritiesByCommandId
        }
        val coverageMaskUniformPackets = geometryPackets.filter { packet ->
            nativeCoverageMaskPlan != null &&
                packet.clipExecutionPlan?.canonicalIdentity() ==
                nativeCoverageMaskPlan.canonicalIdentity()
        }
        val coverageMaskConsumerCommandIds = coverageMaskUniformPackets
            .mapTo(linkedSetOf(), GPUDrawPacket::commandIdValue)
        val legacyUniformPackets = geometryPackets.filterNot {
            it.commandIdValue in preparedAnalyticShapesByCommandId ||
                it.commandIdValue in analyticClipAuthoritiesByCommandId ||
                it.commandIdValue in analyticIntersectionAuthoritiesByCommandId ||
                it in coverageMaskUniformPackets
        }
        val legacyDirectUniformPackets = legacyUniformPackets.filterNot { packet ->
            packet.commandIdValue in pathStencilPlansByCommandId
        }
        val activeDirectUniformLayouts = listOf(
            legacyDirectUniformPackets.isNotEmpty() || pathStencilPlansByCommandId.isNotEmpty(),
            analyticShapeUniformPackets.isNotEmpty(),
            analyticUniformPackets.isNotEmpty(),
            analyticIntersectionUniformPackets.isNotEmpty(),
        ).count { it }
        if (activeDirectUniformLayouts > 1) {
            return refused(
                "unsupported.recording.core_primitive_mixed_uniform_layouts",
                "One direct CorePrimitive pass cannot mix uniform32, uniform64, uniform80, uniform160, " +
                    "or path layouts.",
            )
        }
        val maxBufferSize = if (geometryPackets.isEmpty()) null else limits.maxBufferSize ?: return refused(
            "unsupported.recording.core_primitive_max_buffer_size_unavailable",
            "Direct CorePrimitive uniform slab planning requires observed maxBufferSize.",
        )
        val maxDynamicUniformBuffers = if (geometryPackets.isEmpty()) {
            null
        } else {
            limits.maxDynamicUniformBuffersPerPipelineLayout ?: return refused(
                "unsupported.recording.core_primitive_dynamic_uniform_limit_unavailable",
                "Direct CorePrimitive uniform slab planning requires the observed dynamic-uniform limit.",
            )
        }
        val uniformSlabPlan = if (legacyUniformPackets.isEmpty()) {
            null
        } else {
            when (val planned = GPUUniformSlabPlanner.plan(
                sourceLabel = "core-primitive-uniform-pass",
                deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration.value,
                alignmentBytes = limits.minUniformBufferOffsetAlignment,
                uploadBudgetBytes = minOf(request.configuredAggregateBudgetBytes, requireNotNull(maxBufferSize)),
                maxBufferSize = requireNotNull(maxBufferSize),
                maxDynamicUniformBuffersPerPipelineLayout = requireNotNull(maxDynamicUniformBuffers),
                payloads = legacyUniformPackets.map { packet ->
                    val bytes = requireNotNull(
                        request.semanticsByCommandId.getValue(packet.commandIdValue).payloadRef.uniformBlock,
                    ).bytes
                    GPUUniformSlabPayload(
                        slotLabel = "draw-${packet.commandIdValue}",
                        bytes = ByteArray(bytes.size) { index -> bytes[index].toByte() },
                    )
                },
            )) {
                is GPUUniformSlabPlanningResult.Accepted -> planned.plan
                is GPUUniformSlabPlanningResult.Refused -> return refused(
                    planned.diagnostic.code,
                    "Direct CorePrimitive uniform slab planning was refused.",
                )
            }
        }
        if (uniformSlabPlan != null && uniformSlabPlan.totalBytes > Int.MAX_VALUE.toLong()) {
            return refused(
                "unsupported.recording.core_primitive_uniform_slab_host_size",
                "Direct CorePrimitive uniform slab exceeds the host-addressable packed byte size.",
            )
        }
        val uniformSlabSeal = uniformSlabPlan?.let { plan ->
            val packedBytes = ByteArray(plan.totalBytes.toInt())
            legacyUniformPackets.zip(plan.slots).forEach { (packet, slot) ->
                val bytes = requireNotNull(
                    request.semanticsByCommandId.getValue(packet.commandIdValue).payloadRef.uniformBlock,
                ).bytes
                bytes.indices.forEach { byteIndex ->
                    packedBytes[slot.alignedOffset.toInt() + byteIndex] = bytes[byteIndex].toByte()
                }
            }
            GPUCorePrimitiveUniformSlabSeal(
                plan = plan,
                commandIds = legacyUniformPackets.map(GPUDrawPacket::commandIdValue),
                packedBytes = packedBytes,
            )
        }
        val analyticShapeUniformSlabPlan = if (analyticShapeUniformPackets.isEmpty()) {
            null
        } else {
            when (val planned = GPUUniformSlabPlanner.plan(
                sourceLabel = "core-primitive-analytic-shape-uniform-pass",
                deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration.value,
                alignmentBytes = limits.minUniformBufferOffsetAlignment,
                uploadBudgetBytes = minOf(
                    request.configuredAggregateBudgetBytes,
                    requireNotNull(maxBufferSize),
                ),
                maxBufferSize = requireNotNull(maxBufferSize),
                maxDynamicUniformBuffersPerPipelineLayout = requireNotNull(maxDynamicUniformBuffers),
                payloads = analyticShapeUniformPackets.map { packet ->
                    GPUUniformSlabPayload(
                        slotLabel = "analytic-shape-draw-${packet.commandIdValue}",
                        bytes = preparedAnalyticShapesByCommandId
                            .getValue(packet.commandIdValue)
                            .uniformBytes,
                    )
                },
            )) {
                is GPUUniformSlabPlanningResult.Accepted -> planned.plan
                is GPUUniformSlabPlanningResult.Refused -> return refused(
                    planned.diagnostic.code,
                    "Analytic-shape CorePrimitive uniform80 slab planning was refused.",
                )
            }
        }
        if (analyticShapeUniformSlabPlan != null &&
            analyticShapeUniformSlabPlan.totalBytes > Int.MAX_VALUE.toLong()
        ) {
            return refused(
                "unsupported.recording.core_primitive_analytic_shape_uniform_slab_host_size",
                "Analytic-shape CorePrimitive uniform80 slab exceeds the host-addressable packed byte size.",
            )
        }
        val coverageMaskUniformPayloads = nativeCoverageMaskPlan?.let { maskPlan ->
            val candidate = requireNotNull(coverageMaskPreparedCandidate)
            candidate.producers.zip(maskPlan.producers).map { (snapshot, producer) ->
                GPUUniformSlabPayload(
                    slotLabel = "coverage-mask-producer-${snapshot.sourceOrder}",
                    bytes = corePrimitiveCoverageMaskProducerUniformBytes(maskPlan, producer),
                )
            } + candidate.consumers.map { consumer ->
                GPUUniformSlabPayload(
                    slotLabel = "coverage-mask-consumer-${consumer.packetId.value}",
                    bytes = corePrimitiveCoverageMaskConsumerUniformBytes(
                        maskPlan,
                        request.semanticsByCommandId.getValue(consumer.commandId),
                    ),
                )
            }
        }.orEmpty()
        val coverageMaskUniformSlabPlan = if (coverageMaskUniformPayloads.isEmpty()) {
            null
        } else {
            when (val planned = GPUUniformSlabPlanner.plan(
                sourceLabel = "core-primitive-coverage-mask-uniform-pass",
                deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration.value,
                alignmentBytes = limits.minUniformBufferOffsetAlignment,
                uploadBudgetBytes = minOf(
                    request.configuredAggregateBudgetBytes,
                    requireNotNull(maxBufferSize),
                ),
                maxBufferSize = requireNotNull(maxBufferSize),
                maxDynamicUniformBuffersPerPipelineLayout = requireNotNull(maxDynamicUniformBuffers),
                payloads = coverageMaskUniformPayloads,
            )) {
                is GPUUniformSlabPlanningResult.Accepted -> planned.plan
                is GPUUniformSlabPlanningResult.Refused -> return refused(
                    planned.diagnostic.code,
                    "Coverage-mask CorePrimitive uniform64 slab planning was refused.",
                )
            }
        }
        if (coverageMaskUniformSlabPlan != null &&
            coverageMaskUniformSlabPlan.totalBytes > Int.MAX_VALUE.toLong()
        ) {
            return refused(
                "unsupported.recording.core_primitive_coverage_mask_uniform_slab_host_size",
                "Coverage-mask CorePrimitive uniform64 slab exceeds the host-addressable packed byte size.",
            )
        }
        val coverageMaskUniformSlabSeal = coverageMaskUniformSlabPlan?.let { plan ->
            val maskPlan = requireNotNull(nativeCoverageMaskPlan)
            val candidate = requireNotNull(coverageMaskPreparedCandidate)
            val key = maskPlan.clipResourceKey()
            val packedBytes = ByteArray(plan.totalBytes.toInt())
            coverageMaskUniformPayloads.zip(plan.slots).forEach { (payload, slot) ->
                payload.bytes.copyInto(packedBytes, slot.alignedOffset.toInt())
            }
            val producerCommandId = staticCoverageMaskConsumers.first().commandIdValue
            GPUCorePrimitiveCoverageMaskUniformSlabSeal(
                plan = plan,
                contentKey = maskPlan.contentKey,
                planCanonicalIdentity = maskPlan.canonicalIdentity(),
                maskResource = GPUFrameTargetRef("target.core-primitive.clip-mask.$key"),
                producerSlots = candidate.producers.mapIndexed { index, producer ->
                    val packetId = GPUDrawPacketID(
                        "packet.task.core-primitive.clip-mask.$key.${producer.sourceOrder}",
                    )
                    val renderPipelineKey = producer.structuralKey.stableRenderPipelineKey(
                        CORE_PRIMITIVE_RENDER_PIPELINE_KEY,
                    )
                    GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal(
                        slotIndex = index,
                        sourceOrder = producer.sourceOrder,
                        packetId = packetId,
                        commandId = producerCommandId,
                        structuralPipelineKey = producer.structuralKey,
                        renderPipelineKey = renderPipelineKey,
                        bindingLayoutHash = CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_BINDING_LAYOUT_HASH,
                    )
                },
                consumerSlots = candidate.consumers.mapIndexed { index, consumer ->
                    val renderPipelineKey = consumer.structuralKey.stableRenderPipelineKey(
                        CORE_PRIMITIVE_RENDER_PIPELINE_KEY,
                    )
                    GPUCorePrimitiveCoverageMaskConsumerUniformSlotSeal(
                        slotIndex = candidate.producers.size + index,
                        sourceOrder = consumer.sourceOrder,
                        packetId = consumer.packetId,
                        commandId = consumer.commandId,
                        dependencyFromPreviousConsumerToken = if (index == 0) {
                            null
                        } else {
                            corePrimitiveCoverageMaskConsumerDependencyToken(
                                consumer.packetId,
                                consumer.sourceOrder,
                            )
                        },
                        semanticAuthority = consumer.semanticAuthority,
                        structuralPipelineKey = consumer.structuralKey,
                        renderPipelineKey = renderPipelineKey,
                        bindingLayoutHash = CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_BINDING_LAYOUT_HASH,
                    )
                },
                packedBytes = packedBytes,
            )
        }
        val analyticUniformBytesByCommandId = analyticUniformPackets.associate { packet ->
            val commandId = packet.commandIdValue
            commandId to corePrimitiveAnalyticClipUniformBytes(
                request.semanticsByCommandId.getValue(commandId),
                analyticClipAuthoritiesByCommandId.getValue(commandId),
            )
        }
        val analyticUniformSlabPlan = if (analyticUniformPackets.isEmpty()) {
            null
        } else {
            when (val planned = GPUUniformSlabPlanner.plan(
                sourceLabel = "core-primitive-analytic-clip-uniform-pass",
                deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration.value,
                alignmentBytes = limits.minUniformBufferOffsetAlignment,
                uploadBudgetBytes = minOf(request.configuredAggregateBudgetBytes, requireNotNull(maxBufferSize)),
                maxBufferSize = requireNotNull(maxBufferSize),
                maxDynamicUniformBuffersPerPipelineLayout = requireNotNull(maxDynamicUniformBuffers),
                payloads = analyticUniformPackets.map { packet ->
                    GPUUniformSlabPayload(
                        slotLabel = "analytic-clip-draw-${packet.commandIdValue}",
                        bytes = analyticUniformBytesByCommandId.getValue(packet.commandIdValue),
                    )
                },
            )) {
                is GPUUniformSlabPlanningResult.Accepted -> planned.plan
                is GPUUniformSlabPlanningResult.Refused -> return refused(
                    planned.diagnostic.code,
                    "Analytic-clip CorePrimitive uniform64 slab planning was refused.",
                )
            }
        }
        val analyticIntersectionUniformBytesByCommandId =
            analyticIntersectionUniformPackets.associate { packet ->
                val commandId = packet.commandIdValue
                commandId to corePrimitiveAnalyticIntersectionUniformBytes(
                    request.semanticsByCommandId.getValue(commandId),
                    analyticIntersectionAuthoritiesByCommandId.getValue(commandId),
                )
            }
        val analyticIntersectionUniformSlabPlan = if (analyticIntersectionUniformPackets.isEmpty()) {
            null
        } else {
            when (val planned = GPUUniformSlabPlanner.plan(
                sourceLabel = "core-primitive-analytic-intersection-uniform-pass",
                deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration.value,
                alignmentBytes = limits.minUniformBufferOffsetAlignment,
                uploadBudgetBytes = minOf(request.configuredAggregateBudgetBytes, requireNotNull(maxBufferSize)),
                maxBufferSize = requireNotNull(maxBufferSize),
                maxDynamicUniformBuffersPerPipelineLayout = requireNotNull(maxDynamicUniformBuffers),
                payloads = analyticIntersectionUniformPackets.map { packet ->
                    GPUUniformSlabPayload(
                        slotLabel = "analytic-intersection-draw-${packet.commandIdValue}",
                        bytes = analyticIntersectionUniformBytesByCommandId.getValue(packet.commandIdValue),
                    )
                },
            )) {
                is GPUUniformSlabPlanningResult.Accepted -> planned.plan
                is GPUUniformSlabPlanningResult.Refused -> return refused(
                    planned.diagnostic.code,
                    "Analytic-intersection CorePrimitive uniform160 slab planning was refused.",
                )
            }
        }
        if (analyticIntersectionUniformSlabPlan != null &&
            analyticIntersectionUniformSlabPlan.totalBytes > Int.MAX_VALUE.toLong()
        ) {
            return refused(
                "unsupported.recording.core_primitive_analytic_intersection_uniform_slab_host_size",
                "Analytic-intersection CorePrimitive uniform160 slab exceeds the host-addressable packed byte size.",
            )
        }
        if (analyticUniformSlabPlan != null && analyticUniformSlabPlan.totalBytes > Int.MAX_VALUE.toLong()) {
            return refused(
                "unsupported.recording.core_primitive_analytic_clip_uniform_slab_host_size",
                "Analytic-clip CorePrimitive uniform64 slab exceeds the host-addressable packed byte size.",
            )
        }
        val geometryVertex = geometryBytesByCommandId.takeIf { it.isNotEmpty() }?.let {
            GPUFrameBufferRef("buffer.core-primitive.vertices.${request.baseTaskList.frameId.value}")
        }
        val geometryIndex = geometryBytesByCommandId.takeIf { it.isNotEmpty() }?.let {
            GPUFrameBufferRef("buffer.core-primitive.indices.${request.baseTaskList.frameId.value}")
        }
        val uniformSlab = uniformSlabPlan?.let {
            GPUFrameBufferRef("buffer.core-primitive.uniforms.${request.baseTaskList.frameId.value}")
        }
        val analyticShapeUniformSlab = analyticShapeUniformSlabPlan?.let {
            GPUFrameBufferRef(
                "buffer.core-primitive.analytic-shape-uniforms.${request.baseTaskList.frameId.value}",
            )
        }
        val coverageMaskUniformSlab = coverageMaskUniformSlabPlan?.let {
            GPUFrameBufferRef(
                "buffer.core-primitive.coverage-mask-uniforms.${request.baseTaskList.frameId.value}",
            )
        }
        val analyticUniformSlab = analyticUniformSlabPlan?.let {
            GPUFrameBufferRef("buffer.core-primitive.analytic-clip-uniforms.${request.baseTaskList.frameId.value}")
        }
        val analyticIntersectionUniformSlab = analyticIntersectionUniformSlabPlan?.let {
            GPUFrameBufferRef("buffer.core-primitive.analytic-intersection-uniforms.${request.baseTaskList.frameId.value}")
        }
        val pathDepthStencilBytes = if (pathStencilPlansByCommandId.isEmpty()) {
            null
        } else {
            try {
                corePrimitiveDepthStencilByteSize(request.targetBounds, 1)
            } catch (_: ArithmeticException) {
                return refused(
                    "unsupported.recording.core_primitive_path_depth_stencil_size",
                    "Core primitive path depth/stencil byte size exceeds signed 64-bit arithmetic.",
                )
            }
        }
        val pathDepthStencil = pathDepthStencilBytes?.let {
            GPUFrameTextureRef("texture.core-primitive.path-depth-stencil.${request.baseTaskList.frameId.value}")
        }
        val clipTopologies = clipArtifacts.map { (contentKey, plan) ->
            clipTopology(
                contentKey = contentKey,
                plan = plan,
                target = request.target,
                targetBounds = request.targetBounds,
                representative = basePackets.first { packet ->
                    packet.clipExecutionPlan?.contentKeyOrNull() == contentKey
                },
                recordingId = baseRenders.first { render ->
                    render.drawPackets.any { packet -> packet.clipExecutionPlan?.contentKeyOrNull() == contentKey }
                }.recordingId,
                geometryVertex = geometryVertex,
                geometryIndex = geometryIndex,
                nativeClipStencilPlan = nativeClipStencilPlan,
                nativeCoverageMaskPlan = nativeCoverageMaskPlan,
                coverageMaskUniformSlab = coverageMaskUniformSlab,
                coverageMaskUniformSlabSeal = coverageMaskUniformSlabSeal,
            )
        }
        val preparations = mutableListOf(
            corePrimitiveTargetPreparation(request.target, request.targetBounds),
        )
        if (geometryVertex != null && geometryIndex != null) {
            preparations += corePrimitiveGeometryBufferPreparation(
                geometryVertex,
                geometryVertexBytes,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                "core-primitive.vertices",
            )
            preparations += corePrimitiveGeometryBufferPreparation(
                geometryIndex,
                geometryIndexBytes,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                "core-primitive.indices",
            )
        }
        if (uniformSlabPlan != null && uniformSlab != null) {
            preparations += GPUResourcePreparationRequest(
                resource = uniformSlab,
                descriptor = GPUFrameBufferDescriptor(
                    uniformSlabPlan.totalBytes,
                    uniformSlabPlan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = uniformSlabPlan.totalBytes,
                diagnosticLabel = "core-primitive.uniforms",
            )
        }
        if (analyticShapeUniformSlabPlan != null && analyticShapeUniformSlab != null) {
            preparations += GPUResourcePreparationRequest(
                resource = analyticShapeUniformSlab,
                descriptor = GPUFrameBufferDescriptor(
                    analyticShapeUniformSlabPlan.totalBytes,
                    analyticShapeUniformSlabPlan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = analyticShapeUniformSlabPlan.totalBytes,
                diagnosticLabel = "core-primitive.analytic-shape-uniforms",
            )
        }
        if (coverageMaskUniformSlabPlan != null && coverageMaskUniformSlab != null) {
            preparations += GPUResourcePreparationRequest(
                resource = coverageMaskUniformSlab,
                descriptor = GPUFrameBufferDescriptor(
                    coverageMaskUniformSlabPlan.totalBytes,
                    coverageMaskUniformSlabPlan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = coverageMaskUniformSlabPlan.totalBytes,
                diagnosticLabel = "core-primitive.coverage-mask-uniforms",
            )
        }
        if (analyticUniformSlabPlan != null && analyticUniformSlab != null) {
            preparations += GPUResourcePreparationRequest(
                resource = analyticUniformSlab,
                descriptor = GPUFrameBufferDescriptor(
                    analyticUniformSlabPlan.totalBytes,
                    analyticUniformSlabPlan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = analyticUniformSlabPlan.totalBytes,
                diagnosticLabel = "core-primitive.analytic-clip-uniforms",
            )
        }
        if (analyticIntersectionUniformSlabPlan != null && analyticIntersectionUniformSlab != null) {
            preparations += GPUResourcePreparationRequest(
                resource = analyticIntersectionUniformSlab,
                descriptor = GPUFrameBufferDescriptor(
                    analyticIntersectionUniformSlabPlan.totalBytes,
                    analyticIntersectionUniformSlabPlan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = analyticIntersectionUniformSlabPlan.totalBytes,
                diagnosticLabel = "core-primitive.analytic-intersection-uniforms",
            )
        }
        if (pathDepthStencilBytes != null) {
            preparations += GPUResourcePreparationRequest(
                resource = requireNotNull(pathDepthStencil),
                descriptor = GPUFrameTextureDescriptor(
                    request.targetBounds,
                    GPUColorFormat("depth24plus-stencil8"),
                    1,
                ),
                role = GPUFrameResourceRole.PathDepthStencil,
                usages = setOf(GPUFrameResourceUsage.RenderAttachment),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = pathDepthStencilBytes,
                diagnosticLabel = "core-primitive.path-depth-stencil",
            )
        }
        preparations += clipTopologies.flatMap(GPUCoreClipArtifactTopology::preparations)
        if (readbackPlan != null && staging != null) {
            preparations += GPUResourcePreparationRequest(
                resource = staging,
                descriptor = GPUFrameBufferDescriptor(readbackPlan.stagingDescriptor.minimumBufferBytes, 4L),
                role = GPUFrameResourceRole.ReadbackStaging,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = readbackPlan.stagingDescriptor.minimumBufferBytes,
                diagnosticLabel = "core-primitive.readback",
            )
        }
        val allocations = mutableListOf(
            GPUFrameMemoryAllocation(
                "core-primitive.scene-target",
                GPUFrameMemoryCategory.CanonicalTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
        )
        if (geometryVertex != null && geometryIndex != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.vertices",
                GPUFrameMemoryCategory.ReusableScratch,
                geometryVertexBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.indices",
                GPUFrameMemoryCategory.ReusableScratch,
                geometryIndexBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        if (uniformSlabPlan != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.uniforms",
                GPUFrameMemoryCategory.ReusableScratch,
                uniformSlabPlan.totalBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        if (analyticShapeUniformSlabPlan != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.analytic-shape-uniforms",
                GPUFrameMemoryCategory.ReusableScratch,
                analyticShapeUniformSlabPlan.totalBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        if (coverageMaskUniformSlabPlan != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.coverage-mask-uniforms",
                GPUFrameMemoryCategory.ReusableScratch,
                coverageMaskUniformSlabPlan.totalBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        if (analyticUniformSlabPlan != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.analytic-clip-uniforms",
                GPUFrameMemoryCategory.ReusableScratch,
                analyticUniformSlabPlan.totalBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        if (analyticIntersectionUniformSlabPlan != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.analytic-intersection-uniforms",
                GPUFrameMemoryCategory.ReusableScratch,
                analyticIntersectionUniformSlabPlan.totalBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        if (pathDepthStencilBytes != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.path-depth-stencil",
                GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil,
                pathDepthStencilBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            )
        }
        allocations += clipTopologies.flatMap(GPUCoreClipArtifactTopology::allocations)
        if (readbackPlan != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.readback",
                GPUFrameMemoryCategory.ReadbackStaging,
                readbackPlan.stagingDescriptor.minimumBufferBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        val memoryBudget = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(allocations, request.configuredAggregateBudgetBytes, limits),
        )
        memoryBudget.diagnostic?.let { return GPUCorePrimitivePreparedFrameResult.Refused(it) }

        val prepareId = GPUTaskID("task.core-primitive.prepare.${request.baseTaskList.frameId.value}")
        val topologiesByContentKey = clipTopologies.associateBy(GPUCoreClipArtifactTopology::contentKey)
        val pathDepthStencilLoadStore = GPUDepthStencilLoadStorePlan.WritableStencil(
            GPUStencilLoadOperation.Clear,
            GPUStorePlan.Discard,
            0u,
        )

        fun consumerResourceUses(
            baseRender: GPUTask.Render,
            basePacket: GPUDrawPacket,
            pathPlan: GPUCorePrimitivePathStencilPacketPlan?,
        ): List<GPUFrameResourceUse> {
            val topology = basePacket.clipExecutionPlan?.contentKeyOrNull()?.let(topologiesByContentKey::get)
            val geometryUses = if (
                basePacket.commandIdValue in geometryBytesByCommandId &&
                geometryVertex != null && geometryIndex != null
            ) {
                listOf(
                    GPUFrameResourceUse(
                        geometryVertex,
                        GPUFrameResourceRole.VertexData,
                        GPUFrameResourceUsage.Vertex,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                    GPUFrameResourceUse(
                        geometryIndex,
                        GPUFrameResourceRole.IndexData,
                        GPUFrameResourceUsage.Index,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                )
            } else {
                emptyList()
            }
            val packetUniformSlab = when (basePacket.commandIdValue) {
                in preparedAnalyticShapesByCommandId -> analyticShapeUniformSlab
                in analyticClipAuthoritiesByCommandId -> analyticUniformSlab
                in analyticIntersectionAuthoritiesByCommandId -> analyticIntersectionUniformSlab
                in coverageMaskConsumerCommandIds ->
                    coverageMaskUniformSlab
                else -> uniformSlab
            }
            val uniformUses = if (
                basePacket.commandIdValue in geometryBytesByCommandId && packetUniformSlab != null
            ) {
                listOf(
                    GPUFrameResourceUse(
                        packetUniformSlab,
                        GPUFrameResourceRole.UniformData,
                        GPUFrameResourceUsage.Uniform,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                )
            } else {
                emptyList()
            }
            val pathDepthStencilUses = if (pathPlan != null && pathDepthStencil != null) {
                listOf(
                    GPUFrameResourceUse(
                        pathDepthStencil,
                        GPUFrameResourceRole.PathDepthStencil,
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = true,
                    ),
                )
            } else {
                emptyList()
            }
            return baseRender.resourceUses + geometryUses + uniformUses +
                pathDepthStencilUses + listOfNotNull(topology?.consumerResourceUse)
        }

        fun consumerDepthStencilLoadStore(
            pathPlan: GPUCorePrimitivePathStencilPacketPlan?,
            resourceUses: List<GPUFrameResourceUse>,
        ): GPUDepthStencilLoadStorePlan? = when {
            pathPlan != null -> pathDepthStencilLoadStore
            resourceUses.any { it.role == GPUFrameResourceRole.ClipDepthStencil } ->
                GPUDepthStencilLoadStorePlan.ReadOnlyKeep
            else -> null
        }

        fun isGeometryBatchCompatible(
            commandIds: List<Int>,
            resourceUses: List<GPUFrameResourceUse>,
            depthStencilLoadStore: GPUDepthStencilLoadStorePlan?,
        ): Boolean = commandIds.all { it in geometryBytesByCommandId } &&
            resourceUses.none { it.role == GPUFrameResourceRole.ClipDepthStencil } &&
            (depthStencilLoadStore == null || depthStencilLoadStore == pathDepthStencilLoadStore)

        val geometryBatchPredicted = nativeCoverageMaskPlan == null &&
            baseRenders.isNotEmpty() && baseRenders.all { baseRender ->
            baseRender.drawPackets.all { basePacket ->
                val pathPlan = pathStencilPlansByCommandId[basePacket.commandIdValue]
                val resourceUses = consumerResourceUses(baseRender, basePacket, pathPlan)
                isGeometryBatchCompatible(
                    commandIds = if (pathPlan == null) {
                        listOf(basePacket.commandIdValue)
                    } else {
                        listOf(basePacket.commandIdValue, basePacket.commandIdValue)
                    },
                    resourceUses = resourceUses,
                    depthStencilLoadStore = consumerDepthStencilLoadStore(pathPlan, resourceUses),
                )
            }
        }
        val directPathDepthStencilCompatible =
            pathStencilPlansByCommandId.isNotEmpty() && geometryBatchPredicted
        val publicPipelineKeys = mutableMapOf<GPUCorePrimitiveRenderPipelineStructuralKey, GPURenderPipelineKey>()
        val consumersByBaseTask = linkedMapOf<GPUTaskID, List<GPUTask.Render>>()
        var consumerOrdinal = 0
        baseRenders.forEach { baseRender ->
            consumersByBaseTask[baseRender.taskId] = baseRender.drawPackets.mapIndexed { packetIndex, basePacket ->
                val pathPlan = pathStencilPlansByCommandId[basePacket.commandIdValue]
                val preparedPackets = if (pathPlan == null) {
                    listOf(
                        packet(
                            basePacket,
                            requireNotNull(request.semanticsByCommandId[basePacket.commandIdValue]),
                            preparedSemanticOverride = preparedCoverageMaskSemanticsByCommandId[
                                basePacket.commandIdValue
                            ] ?: preparedAnalyticShapesByCommandId[basePacket.commandIdValue]?.semantic,
                            direct = basePacket.commandIdValue in directGeometryBytesByCommandId ||
                                basePacket.commandIdValue in
                                nativeClipStencilConsumerGeometryBytesByCommandId,
                            clipStencilCompatible = basePacket.commandIdValue in
                                nativeClipStencilConsumerGeometryBytesByCommandId,
                            pathDepthStencilCompatible = directPathDepthStencilCompatible &&
                                basePacket.commandIdValue in directGeometryBytesByCommandId,
                            uniformSlabSeal = uniformSlabSeal,
                            analyticShape = preparedAnalyticShapesByCommandId[basePacket.commandIdValue],
                            analyticShapeUniformSlabPlan = analyticShapeUniformSlabPlan,
                            analyticClipAuthority = analyticClipAuthoritiesByCommandId[basePacket.commandIdValue],
                            analyticUniformSlabPlan = analyticUniformSlabPlan,
                            analyticUniformBytes = analyticUniformBytesByCommandId[basePacket.commandIdValue],
                            analyticIntersectionAuthority =
                                analyticIntersectionAuthoritiesByCommandId[basePacket.commandIdValue],
                            analyticIntersectionUniformSlabPlan = analyticIntersectionUniformSlabPlan,
                            analyticIntersectionUniformBytes =
                                analyticIntersectionUniformBytesByCommandId[basePacket.commandIdValue],
                            coverageMaskUniformSlabSeal = coverageMaskUniformSlabSeal,
                            publicPipelineKeys = publicPipelineKeys,
                        ),
                    )
                } else {
                    listOf(
                        pathStencilPacket(
                            basePacket,
                            pathPlan,
                            GPUDrawPacketRole.PathStencilProducer,
                            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                            corePrimitiveColorWriteNoneBlendPlan(),
                            requireNotNull(uniformSlabSeal),
                            publicPipelineKeys,
                        ),
                        pathStencilPacket(
                            basePacket,
                            pathPlan,
                            GPUDrawPacketRole.PathStencilCover,
                            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                            requireNotNull(basePacket.blendPlan),
                            requireNotNull(uniformSlabSeal),
                            publicPipelineKeys,
                        ),
                    )
                }
                val resourceUses = consumerResourceUses(baseRender, basePacket, pathPlan)
                val batchEligibility = baseRender.batchEligibilityByPacketId[basePacket.packetId]
                    ?: producerBatchEligibility()
                GPUTask.Render(
                    taskId = GPUTaskID("${baseRender.taskId.value}.core-consumer.$packetIndex"),
                    recordingId = baseRender.recordingId,
                    phase = GPUTaskPhase.Render,
                    target = request.target,
                    loadStore = GPULoadStorePlan(
                        if (consumerOrdinal++ == 0) "clear" else "load",
                        GPUStorePlan.Store,
                    ),
                    samplePlan = GPUSamplePlan.SingleSampleFrame,
                    resourceUses = resourceUses,
                    provisionalSegmentKey = baseRender.provisionalSegmentKey,
                    drawPackets = preparedPackets,
                    batchEligibilityByPacketId = preparedPackets.associate { packet ->
                        packet.packetId to batchEligibility
                    },
                    sampleContinuationKey = null,
                    compositeMembership = baseRender.compositeMembership,
                    depthStencilLoadStore = consumerDepthStencilLoadStore(pathPlan, resourceUses),
                )
            }
        }
        val unbatchedPreparedRenders = consumersByBaseTask.values.flatten()
        val geometryBatchConstructedCompatible = nativeCoverageMaskPlan == null &&
            unbatchedPreparedRenders.isNotEmpty() &&
            unbatchedPreparedRenders.all { render ->
                isGeometryBatchCompatible(
                    commandIds = render.drawPackets.map(GPUDrawPacket::commandIdValue),
                    resourceUses = render.resourceUses,
                    depthStencilLoadStore = render.depthStencilLoadStore,
                )
            }
        if (geometryBatchPredicted != geometryBatchConstructedCompatible) {
            return refused(
                "invalid.recording.core_primitive_geometry_batch_prediction",
                "Core primitive geometry batch prediction diverged from its constructed renders.",
            )
        }
        val geometryBatch = unbatchedPreparedRenders.takeIf { geometryBatchConstructedCompatible }?.let { renders ->
            val packets = renders.flatMap(GPUTask.Render::drawPackets)
            val hasPathStencil = pathStencilPlansByCommandId.isNotEmpty()
            GPUTask.Render(
                taskId = GPUTaskID(
                    "task.core-primitive.${if (hasPathStencil) "path-stencil" else "direct"}-batch." +
                        request.baseTaskList.frameId.value,
                ),
                recordingId = renders.first().recordingId,
                phase = GPUTaskPhase.Render,
                target = request.target,
                loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                resourceUses = renders.flatMap(GPUTask.Render::resourceUses).distinct(),
                provisionalSegmentKey = GPUProvisionalRenderSegmentKey(
                    "core-primitive.${if (hasPathStencil) "path-stencil" else "direct"}-batch." +
                        request.baseTaskList.frameId.value,
                ),
                drawPackets = packets,
                batchEligibilityByPacketId = renders
                    .flatMap { render -> render.batchEligibilityByPacketId.entries }
                    .associate { it.toPair() },
                depthStencilLoadStore = pathDepthStencilLoadStore.takeIf { hasPathStencil },
            )
        }
        val preparedRenders = geometryBatch?.let(::listOf) ?: unbatchedPreparedRenders
        if (nativeClipStencilPlan != null) {
            val planIdentity = nativeClipStencilPlan.canonicalIdentity()
            val topology = clipTopologies.single { it.contentKey == nativeClipStencilPlan.contentKey }
            val producerPacket = topology.producerTasks.single().drawPackets.single()
            val consumerPackets = preparedRenders.flatMap(GPUTask.Render::drawPackets).filter { packet ->
                packet.clipExecutionPlan?.canonicalIdentity() == planIdentity
            }
            val path = requireNotNull(nativeClipStencilPlan.corePrimitiveClipStencilNativePathOrNull())
            val fan = requireNotNull(nativeClipStencilProducerFan)
            val candidate = GPUCorePrimitiveClipStencilPreparedCandidate(
                contentKey = nativeClipStencilPlan.contentKey,
                planCanonicalIdentity = planIdentity,
                producerPacketId = producerPacket.packetId,
                producerCommandId = producerPacket.commandIdValue,
                producerNdcVertices = requireNotNull(nativeClipStencilProducerNdcVertices),
                producerContourStarts = path.contourStarts,
                producerFanVertices = fan.vertices.toList(),
                producerFanIndices = fan.indices.toList(),
                producerStructuralKey =
                    corePrimitiveClipStencilProducerRenderPipelineStructuralKey(path.fillRule),
                consumers = consumerPackets.mapIndexed { index, packet ->
                    GPUCorePrimitiveClipStencilPreparedCandidate.Consumer(
                        packetId = packet.packetId,
                        commandId = packet.commandIdValue,
                        sourceOrder = packet.originalPaintOrder,
                        structuralKey = requireNotNull(packet.corePrimitivePreparedAuthority)
                            .structuralPipelineKey,
                        dependencyFromPreviousConsumerToken = if (index == 0) {
                            null
                        } else {
                            corePrimitiveClipStencilConsumerDependencyToken(packet.commandIdValue)
                        },
                    )
                },
                attachmentLogicalReference = topology.consumerResourceUse.resource.value,
                attachmentWidth = request.targetBounds.width,
                attachmentHeight = request.targetBounds.height,
                attachmentSampleCount = 1,
            )
            producerPacket.attachCorePrimitiveClipStencilPreparedCandidate(candidate)
            consumerPackets.forEach { packet ->
                packet.attachCorePrimitiveClipStencilPreparedCandidate(candidate)
            }
        }
        val invalidPathDepthStencilRender = preparedRenders.firstOrNull { render ->
            val pathUses = render.resourceUses.filter { it.role == GPUFrameResourceRole.PathDepthStencil }
            val clipUses = render.resourceUses.filter { it.role == GPUFrameResourceRole.ClipDepthStencil }
            val exactPathAttachment = pathDepthStencil != null && pathUses.size == 1 &&
                pathUses.single() == GPUFrameResourceUse(
                    pathDepthStencil,
                    GPUFrameResourceRole.PathDepthStencil,
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceLifetime.FrameLocal,
                    write = true,
                ) && render.depthStencilLoadStore == pathDepthStencilLoadStore
            val hasPathAttachmentState = pathUses.isNotEmpty() ||
                render.depthStencilLoadStore == pathDepthStencilLoadStore
            val exactClipConsumerAttachment =
                render.drawPackets.all { packet ->
                    packet.commandIdValue in nativeClipStencilConsumerGeometryBytesByCommandId
                } && clipUses.size == 1 && !clipUses.single().write &&
                    clipUses.single().usage == GPUFrameResourceUsage.RenderAttachment &&
                    clipUses.single().lifetime == GPUFrameResourceLifetime.FrameLocal &&
                    render.depthStencilLoadStore == GPUDepthStencilLoadStorePlan.ReadOnlyKeep
            val exactCoverageMaskConsumer = nativeCoverageMaskPlan != null &&
                render.drawPackets.all { packet ->
                    packet.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal ===
                        coverageMaskUniformSlabSeal
                } && clipUses.isEmpty() && render.depthStencilLoadStore == null &&
                render.resourceUses.count { it.role == GPUFrameResourceRole.ClipMask } == 1 &&
                render.resourceUses.count { it.role == GPUFrameResourceRole.UniformData } == 1
            val authorities = render.drawPackets.mapNotNull(GPUDrawPacket::corePrimitivePreparedAuthority)
            authorities.size != render.drawPackets.size ||
                hasPathAttachmentState != exactPathAttachment ||
                authorities.any { authority ->
                    val structuralKey = authority.structuralPipelineKey
                    when (structuralKey.role) {
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading -> if (exactPathAttachment) {
                            structuralKey.depthStencil != corePrimitiveDirectPathDepthStencilState()
                        } else {
                            structuralKey.depthStencil !=
                                GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None
                        }
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                        -> !exactPathAttachment
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilConsumer ->
                            !exactClipConsumerAttachment
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer ->
                            !exactCoverageMaskConsumer || structuralKey.depthStencil !=
                            GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.ClipStencilProducer,
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskProducer,
                        -> true
                    }
                }
        }
        if (invalidPathDepthStencilRender != null) {
            return refused(
                "invalid.recording.core_primitive_path_depth_stencil_authority",
                "Core primitive pipeline depth/stencil authority must exactly match its render attachment.",
            )
        }
        if (geometryBatch != null) {
            baseRenders.forEach { baseRender -> consumersByBaseTask[baseRender.taskId] = listOf(geometryBatch) }
        }
        val tasks = mutableListOf<GPUTask>(
            GPUTask.PrepareResources(
                prepareId,
                preparedRenders.first().recordingId,
                GPUTaskPhase.Prepare,
                preparations,
            ),
        )
        tasks += clipTopologies.flatMap(GPUCoreClipArtifactTopology::producerTasks)
        tasks += preparedRenders
        val baseRenderIds = baseRenders.map(GPUTask.Render::taskId).toSet()
        val baseDependencies = request.baseTaskList.dependencies.filter { dependency ->
            dependency.fromTaskId in baseRenderIds && dependency.toTaskId in baseRenderIds
        }
        if (baseDependencies.size != request.baseTaskList.dependencies.size) {
            return refused(
                "unsupported.recording.core_primitive_base_dependencies",
                "Prepared core primitives cannot discard non-render base dependencies.",
            )
        }
        val dependencies = clipTopologies
            .flatMap(GPUCoreClipArtifactTopology::producerDependencies)
            .toMutableList()
        baseDependencies.forEach { dependency ->
            val translated = dependency.copy(
                fromTaskId = consumersByBaseTask.getValue(dependency.fromTaskId).last().taskId,
                toTaskId = consumersByBaseTask.getValue(dependency.toTaskId).first().taskId,
            )
            if (translated.fromTaskId != translated.toTaskId && nativeClipStencilPlan == null &&
                nativeCoverageMaskPlan == null
            ) {
                dependencies += translated
            }
        }
        fun addPreparedOrderIfMissing(
            from: GPUTask.Render,
            to: GPUTask.Render,
            index: Int,
        ) {
            if (dependencies.none { it.fromTaskId == from.taskId && it.toTaskId == to.taskId }) {
                val toPacket = to.drawPackets.singleOrNull()
                val canonicalToken = toPacket
                    ?.corePrimitiveClipStencilPreparedCandidate
                    ?.consumers
                    ?.singleOrNull { consumer -> consumer.packetId == toPacket.packetId }
                    ?.dependencyFromPreviousConsumerToken
                    ?: toPacket?.corePrimitivePreparedAuthority
                        ?.coverageMaskUniformSlabSeal
                        ?.consumerSlots
                        ?.singleOrNull { consumer -> consumer.packetId == toPacket.packetId }
                        ?.dependencyFromPreviousConsumerToken
                val sealedToken = canonicalToken?.let(::GPUTaskUseToken)
                dependencies += dependency(from.taskId, to.taskId, index).let { edge ->
                    if (sealedToken == null) edge else edge.copy(useToken = sealedToken)
                }
            }
        }
        if (nativeCoverageMaskPlan == null) {
            consumersByBaseTask.values.forEach { consumers ->
                consumers.zipWithNext().forEachIndexed { index, (from, to) ->
                    addPreparedOrderIfMissing(from, to, dependencies.size + index)
                }
            }
        }
        val producedContentKeys = mutableSetOf<String>()
        var previousConsumer: GPUTask.Render? = null
        preparedRenders.forEachIndexed { index, consumer ->
            val previous = previousConsumer
            val plan = consumer.drawPackets.singleOrNull()?.clipExecutionPlan
            val contentKey = plan?.contentKeyOrNull()
            val topology = contentKey?.let(topologiesByContentKey::get)
            val isNativeCoverageTopology = nativeCoverageMaskPlan != null &&
                topology?.contentKey == nativeCoverageMaskPlan.contentKey
            val firstArtifactUse = topology != null && producedContentKeys.add(topology.contentKey)
            if (topology != null && firstArtifactUse && !isNativeCoverageTopology) {
                val firstProducer = topology.producerTasks.first().taskId
                dependencies += if (previous == null) {
                    dependency(prepareId, firstProducer, dependencies.size + index)
                } else {
                    clipDependency(
                        previous.taskId,
                        firstProducer,
                        topology.orderingToken,
                        "paint-before-producer",
                        topology.atomicGroupId,
                    )
                }
            }
            when {
                isNativeCoverageTopology && firstArtifactUse -> {
                    dependencies += clipDependency(
                        requireNotNull(topology).finalProducerId,
                        consumer.taskId,
                        topology.orderingToken,
                        "producer-before-consumer",
                        topology.atomicGroupId,
                    )
                }
                isNativeCoverageTopology && previous != null ->
                    addPreparedOrderIfMissing(previous, consumer, dependencies.size + index)
                topology != null -> {
                    dependencies += clipDependency(
                        topology.finalProducerId,
                        consumer.taskId,
                        topology.orderingToken,
                        "producer-before-consumer",
                        topology.atomicGroupId,
                    )
                    if (previous != null && !firstArtifactUse) {
                        addPreparedOrderIfMissing(previous, consumer, dependencies.size + index)
                    }
                }
                previous != null ->
                    addPreparedOrderIfMissing(previous, consumer, dependencies.size + index)
                else -> dependencies += dependency(prepareId, consumer.taskId, dependencies.size + index)
            }
            previousConsumer = consumer
        }
        if (readbackRequest != null && staging != null) {
            val readbackId = GPUTaskID("task.core-primitive.readback.${request.baseTaskList.frameId.value}")
            tasks += GPUTask.Readback(
                readbackId,
                preparedRenders.last().recordingId,
                GPUTaskPhase.Readback,
                request.target,
                staging,
                readbackRequest,
            )
            dependencies += dependency(preparedRenders.last().taskId, readbackId, dependencies.size)
        }
        return GPUCorePrimitivePreparedFrameResult.Recorded(
            GPUTaskList(
                frameId = request.baseTaskList.frameId,
                capabilitySeal = request.baseTaskList.capabilitySeal,
                recordingSeals = request.baseTaskList.recordingSeals,
                expectedReplayKeyHash = request.baseTaskList.expectedReplayKeyHash,
                tasks = tasks,
                dependencies = dependencies.distinct(),
                phaseOrder = request.baseTaskList.phaseOrder,
                memoryBudget = memoryBudget,
                diagnostics = request.baseTaskList.diagnostics,
            ),
        )
    }

    private fun clipTopology(
        contentKey: String,
        plan: GPUClipExecutionPlan,
        target: GPUFrameTargetRef,
        targetBounds: GPUPixelBounds,
        representative: GPUDrawPacket,
        recordingId: GPURecordingID,
        geometryVertex: GPUFrameBufferRef?,
        geometryIndex: GPUFrameBufferRef?,
        nativeClipStencilPlan: GPUClipExecutionPlan.StencilCoverage?,
        nativeCoverageMaskPlan: GPUClipExecutionPlan.CoverageMask?,
        coverageMaskUniformSlab: GPUFrameBufferRef?,
        coverageMaskUniformSlabSeal: GPUCorePrimitiveCoverageMaskUniformSlabSeal?,
    ): GPUCoreClipArtifactTopology {
        val key = plan.clipResourceKey()
        return when (plan) {
            is GPUClipExecutionPlan.StencilCoverage -> {
                val nativePath = nativeClipStencilPlan
                    ?.takeIf { it.canonicalIdentity() == plan.canonicalIdentity() }
                    ?.corePrimitiveClipStencilNativePathOrNull()
                val depthStencilBytes = corePrimitiveDepthStencilByteSize(targetBounds, plan.sampleCount)
                val resource = GPUFrameTextureRef("texture.core-primitive.clip-depth-stencil.$key")
                val producerId = GPUTaskID("task.core-primitive.clip-stencil.$key")
                val packet = clipProducerPacket(
                    base = representative,
                    plan = plan,
                    taskId = producerId,
                    role = GPUDrawPacketRole.StencilProducer,
                    renderStep = "clip.stencil.producer",
                    variant = "stencil",
                    authority = GPUClipProducerAuthority.Stencil(plan.producer),
                    nativeClipStencilPath = nativePath,
                )
                val use = GPUFrameResourceUse(
                    resource,
                    GPUFrameResourceRole.ClipDepthStencil,
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceLifetime.FrameLocal,
                    true,
                )
                val producerUses = buildList {
                    if (nativePath != null) {
                        add(
                            GPUFrameResourceUse(
                                requireNotNull(geometryVertex),
                                GPUFrameResourceRole.VertexData,
                                GPUFrameResourceUsage.Vertex,
                                GPUFrameResourceLifetime.FrameLocal,
                                false,
                            ),
                        )
                        add(
                            GPUFrameResourceUse(
                                requireNotNull(geometryIndex),
                                GPUFrameResourceRole.IndexData,
                                GPUFrameResourceUsage.Index,
                                GPUFrameResourceLifetime.FrameLocal,
                                false,
                            ),
                        )
                    }
                    add(use)
                }
                GPUCoreClipArtifactTopology(
                    contentKey,
                    listOf(
                        GPUResourcePreparationRequest(
                            resource,
                            GPUFrameTextureDescriptor(
                                targetBounds,
                                GPUColorFormat("depth24plus-stencil8"),
                                plan.sampleCount,
                            ),
                            GPUFrameResourceRole.ClipDepthStencil,
                            setOf(GPUFrameResourceUsage.RenderAttachment),
                            GPUFrameResourceLifetime.FrameLocal,
                            depthStencilBytes,
                            "core-primitive.clip-depth-stencil.$key",
                        ),
                    ),
                    listOf(
                        GPUFrameMemoryAllocation(
                            "core-primitive.clip-depth-stencil.$key",
                            GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil,
                            depthStencilBytes,
                            GPUFrameMemoryResourceKind.Texture2D,
                            targetBounds,
                        ),
                    ),
                    listOf(
                        GPUTask.Render(
                            producerId,
                            recordingId,
                            GPUTaskPhase.Render,
                            target,
                            GPULoadStorePlan("load", GPUStorePlan.Store),
                            GPUSamplePlan.SingleSampleFrame,
                            producerUses,
                            GPUProvisionalRenderSegmentKey("clip.stencil.$key"),
                            listOf(packet),
                            mapOf(packet.packetId to producerBatchEligibility()),
                            depthStencilLoadStore = GPUDepthStencilLoadStorePlan.WritableStencil(
                                loadOperation = when (plan.producer.loadOperation) {
                                    GPUClipStencilLoadOperation.Clear -> GPUStencilLoadOperation.Clear
                                    GPUClipStencilLoadOperation.Load -> GPUStencilLoadOperation.Load
                                },
                                storeOperation = when (plan.producer.storeOperation) {
                                    GPUClipStencilStoreOperation.Store -> GPUStorePlan.Store
                                    GPUClipStencilStoreOperation.Discard -> GPUStorePlan.Discard
                                },
                                clearValue = plan.producer.clearValue,
                            ),
                        ),
                    ),
                    emptyList(),
                    producerId,
                    use.copy(write = false),
                    plan.orderingToken.value,
                    plan.atomicGroup.value,
                )
            }
            is GPUClipExecutionPlan.CoverageMask -> {
                val nativeMask = nativeCoverageMaskPlan?.canonicalIdentity() == plan.canonicalIdentity()
                val mask = coverageMaskUniformSlabSeal?.maskResource
                    ?.takeIf { nativeMask }
                    ?: GPUFrameTargetRef("target.core-primitive.clip-mask.$key")
                val maskUse = GPUFrameResourceUse(
                    mask,
                    GPUFrameResourceRole.ClipMask,
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceLifetime.FrameLocal,
                    true,
                )
                val preparations = mutableListOf(
                    GPUResourcePreparationRequest(
                        mask,
                        GPUFrameTextureDescriptor(plan.bounds, GPUColorFormat("rgba8unorm"), 1),
                        GPUFrameResourceRole.ClipMask,
                        setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
                        GPUFrameResourceLifetime.FrameLocal,
                        plan.resolvedBytes,
                        "core-primitive.clip-mask.$key",
                    ),
                )
                val allocations = mutableListOf(
                    GPUFrameMemoryAllocation(
                        "core-primitive.clip-mask.$key",
                        GPUFrameMemoryCategory.ReusableScratch,
                        plan.resolvedBytes,
                        GPUFrameMemoryResourceKind.Texture2D,
                        plan.bounds,
                    ),
                )
                val producerUses = mutableListOf(maskUse)
                if (nativeMask) {
                    producerUses += GPUFrameResourceUse(
                        requireNotNull(coverageMaskUniformSlab),
                        GPUFrameResourceRole.UniformData,
                        GPUFrameResourceUsage.Uniform,
                        GPUFrameResourceLifetime.FrameLocal,
                        false,
                    )
                }
                if (plan.depthStencilRequired) {
                    val depthStencil = GPUFrameTextureRef("texture.core-primitive.clip-mask-depth-stencil.$key")
                    val depthUse = GPUFrameResourceUse(
                        depthStencil,
                        GPUFrameResourceRole.ClipDepthStencil,
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceLifetime.FrameLocal,
                        true,
                    )
                    producerUses += depthUse
                    preparations += GPUResourcePreparationRequest(
                        depthStencil,
                        GPUFrameTextureDescriptor(
                            plan.bounds,
                            GPUColorFormat("depth24plus-stencil8"),
                            plan.sampleCount,
                        ),
                        GPUFrameResourceRole.ClipDepthStencil,
                        setOf(GPUFrameResourceUsage.RenderAttachment),
                        GPUFrameResourceLifetime.FrameLocal,
                        plan.depthStencilBytes,
                        "core-primitive.clip-mask-depth-stencil.$key",
                    )
                    allocations += GPUFrameMemoryAllocation(
                        "core-primitive.clip-mask-depth-stencil.$key",
                        GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil,
                        plan.depthStencilBytes,
                        GPUFrameMemoryResourceKind.Texture2D,
                        plan.bounds,
                    )
                }
                val producerTasks = plan.producers.mapIndexed { index, producer ->
                    val producerId = GPUTaskID("task.core-primitive.clip-mask.$key.${producer.sourceOrder}")
                    val packet = clipProducerPacket(
                        base = representative,
                        plan = plan,
                        taskId = producerId,
                        role = GPUDrawPacketRole.ClipProducer,
                        renderStep = "clip.mask.producer",
                        variant = "${producer.combine.name}.${producer.sourceOrder}",
                        authority = GPUClipProducerAuthority.Mask(producer),
                        nativeClipStencilPath = null,
                        coverageMaskProducerSlot = coverageMaskUniformSlabSeal?.producerSlots
                            ?.singleOrNull { slot -> slot.sourceOrder == producer.sourceOrder },
                        coverageMaskUniformSlabSeal = coverageMaskUniformSlabSeal,
                    )
                    GPUTask.Render(
                        producerId,
                        recordingId,
                        GPUTaskPhase.Render,
                        mask,
                        GPULoadStorePlan(
                            loadOp = if (index == 0) "clear" else "load",
                            storePlan = GPUStorePlan.Store,
                            clearColorLabel = if (index == 0) CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL else null,
                        ),
                        GPUSamplePlan.SingleSampleFrame,
                        producerUses,
                        GPUProvisionalRenderSegmentKey("clip.mask.$key.${producer.sourceOrder}"),
                        listOf(packet),
                        mapOf(packet.packetId to producerBatchEligibility()),
                    )
                }
                val producerDependencies = producerTasks.zipWithNext().mapIndexed { index, (from, to) ->
                    clipDependency(from.taskId, to.taskId, plan.orderingToken.value, "mask-producer.$index", null)
                }
                GPUCoreClipArtifactTopology(
                    contentKey,
                    preparations,
                    allocations,
                    producerTasks,
                    producerDependencies,
                    producerTasks.last().taskId,
                    GPUFrameResourceUse(
                        mask,
                        GPUFrameResourceRole.ClipMask,
                        GPUFrameResourceUsage.TextureBinding,
                        GPUFrameResourceLifetime.FrameLocal,
                        false,
                    ),
                    plan.orderingToken.value,
                    null,
                )
            }
            GPUClipExecutionPlan.NoClip,
            is GPUClipExecutionPlan.ScissorOnly,
            is GPUClipExecutionPlan.AnalyticCoverage,
            is GPUClipExecutionPlan.AnalyticIntersection,
            is GPUClipExecutionPlan.Refused,
            -> error("Non-resource clip plans do not create artifact topology")
        }
    }

    private fun clipProducerPacket(
        base: GPUDrawPacket,
        plan: GPUClipExecutionPlan,
        taskId: GPUTaskID,
        role: GPUDrawPacketRole,
        renderStep: String,
        variant: String,
        authority: GPUClipProducerAuthority,
        nativeClipStencilPath: GPUClipExecutionGeometry.Path?,
        coverageMaskProducerSlot: GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal? = null,
        coverageMaskUniformSlabSeal: GPUCorePrimitiveCoverageMaskUniformSlabSeal? = null,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.${taskId.value}"),
        commandIdValue = base.commandIdValue,
        analysisRecordId = "analysis.${taskId.value}",
        passId = "pass.${taskId.value}",
        layerId = base.layerId,
        bindingListId = "bindings.${taskId.value}",
        insertionReasonCode = "$renderStep.$variant",
        sortKey = base.sortKey,
        sortKeyPreimage = base.sortKeyPreimage,
        renderStepId = GPURenderStepID(renderStep),
        renderStepVersion = 1,
        role = role,
        blendPlan = corePrimitiveClipProducerBlendPlan(authority),
        renderPipelineKey = coverageMaskProducerSlot?.renderPipelineKey ?: nativeClipStencilPath?.let { path ->
            corePrimitiveClipStencilProducerRenderPipelineStructuralKey(path.fillRule)
                .stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
        } ?: corePrimitiveClipProducerPipelineKey(plan, authority),
        bindingLayoutHash = coverageMaskProducerSlot?.bindingLayoutHash ?: "layout.$renderStep.none",
        vertexSourceLabel = if (nativeClipStencilPath == null) {
            "clip-producer-authority"
        } else {
            CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
        },
        targetStateHash = "target.$renderStep.single-sample",
        originalPaintOrder = base.originalPaintOrder,
        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        frameProvenance = base.frameProvenance,
        clipCoveragePlan = base.clipCoveragePlan,
        clipExecutionPlan = plan,
        clipProducerAuthority = authority,
    ).let { packet ->
        if (coverageMaskProducerSlot == null) {
            packet
        } else {
            require(packet.packetId == coverageMaskProducerSlot.packetId &&
                packet.commandIdValue == coverageMaskProducerSlot.commandId
            ) { "Coverage-mask producer packet must match its exact uniform slot authority" }
            packet.attachCorePrimitivePreparedAuthority(
                GPUCorePrimitivePreparedPacketAuthority(
                    structuralPipelineKey = coverageMaskProducerSlot.structuralPipelineKey,
                    renderPipelineKey = coverageMaskProducerSlot.renderPipelineKey,
                    uniformSlabSeal = null,
                    coverageMaskUniformSlabSeal = requireNotNull(coverageMaskUniformSlabSeal),
                ),
            )
        }
    }

    private fun producerBatchEligibility() = GPUPassBatchEligibility(
        kind = GPUPassBatchKind.SolidFill,
        queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
    )

    private fun clipDependency(
        from: GPUTaskID,
        to: GPUTaskID,
        orderingToken: String,
        reason: String,
        atomicGroupId: String?,
    ) = GPUTaskDependency(
        from,
        to,
        "clip-producer-consumer",
        GPUTaskUseToken(orderingToken),
        "preserve.core-primitive.clip.$reason",
        atomicGroupId?.let(::GPUTaskAtomicGroupID),
    )

    private fun pathStencilPacket(
        basePacket: GPUDrawPacket,
        pathPlan: GPUCorePrimitivePathStencilPacketPlan,
        packetRole: GPUDrawPacketRole,
        structuralRole: GPUCorePrimitiveRenderPipelineStructuralKey.Role,
        blendPlan: GPUBlendPlan,
        uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal,
        publicPipelineKeys: MutableMap<GPUCorePrimitiveRenderPipelineStructuralKey, GPURenderPipelineKey>,
    ): GPUDrawPacket {
        require(
            packetRole == GPUDrawPacketRole.PathStencilProducer &&
                structuralRole == GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer ||
                packetRole == GPUDrawPacketRole.PathStencilCover &&
                structuralRole == GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
        ) { "Path stencil packet and structural roles must agree" }
        val clipExecutionPlan = requireNotNull(basePacket.clipExecutionPlan)
        val preparedSemantic = pathPlan.semantic.withPathPacketState(
            scissorBounds = pathPlan.scissorBounds,
            clipExecutionPlanIdentity = clipExecutionPlan.canonicalIdentity(),
            blendPlanIdentity = blendPlan.canonicalIdentity(),
        )
        val structuralPipelineKey = corePrimitivePathStencilRenderPipelineStructuralKey(
            preparedSemantic,
            structuralRole,
            clipExecutionPlan,
            blendPlan,
        )
        val renderPipelineKey = publicPipelineKeys.getOrPut(structuralPipelineKey) {
            structuralPipelineKey.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
        }
        val roleLabel = if (packetRole == GPUDrawPacketRole.PathStencilProducer) "producer" else "cover"
        return GPUDrawPacket(
            packetId = GPUDrawPacketID("${basePacket.packetId.value}.path-stencil-$roleLabel"),
            commandIdValue = basePacket.commandIdValue,
            analysisRecordId = basePacket.analysisRecordId,
            passId = "pass.core-primitive.path-stencil",
            layerId = basePacket.layerId,
            bindingListId = basePacket.bindingListId,
            insertionReasonCode = "core-primitive.path-stencil-$roleLabel",
            sortKey = basePacket.sortKey,
            sortKeyPreimage = basePacket.sortKeyPreimage,
            renderStepId = GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
            renderStepVersion = 1,
            role = packetRole,
            blendPlan = blendPlan,
            renderPipelineKey = renderPipelineKey,
            bindingLayoutHash = CORE_PRIMITIVE_BINDING_LAYOUT_HASH,
            uniformSlot = preparedSemantic.payloadRef.uniformSlot,
            resourceSlot = basePacket.resourceSlot,
            semanticPayload = preparedSemantic,
            vertexSourceLabel = CORE_PRIMITIVE_VERTEX_SOURCE_LABEL,
            scissorBoundsHash = corePrimitiveScissorAuthority(preparedSemantic.scissorBounds),
            targetStateHash = CORE_PRIMITIVE_TARGET_STATE_HASH,
            originalPaintOrder = basePacket.originalPaintOrder,
            resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
            frameProvenance = basePacket.frameProvenance,
            clipCoveragePlan = basePacket.clipCoveragePlan,
            clipExecutionPlan = clipExecutionPlan,
            diagnostics = basePacket.diagnostics,
        ).attachCorePrimitivePreparedAuthority(
            GPUCorePrimitivePreparedPacketAuthority(
                structuralPipelineKey = structuralPipelineKey,
                renderPipelineKey = renderPipelineKey,
                uniformSlabSeal = uniformSlabSeal,
            ),
        )
    }

    private fun GPUDrawSemanticPayload.CorePrimitive.withPathPacketState(
        scissorBounds: GPUPixelBounds,
        clipExecutionPlanIdentity: String,
        blendPlanIdentity: String,
    ) = GPUDrawSemanticPayload.CorePrimitive(
        payloadRef = payloadRef,
        sourceFamily = sourceFamily,
        geometry = geometry,
        premultipliedRgba = premultipliedRgba,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlanIdentity = clipExecutionPlanIdentity,
        blendPlanIdentity = blendPlanIdentity,
        frameProvenance = frameProvenance,
        coverageMode = coverageMode,
        analysisRecordId = analysisRecordId,
        analysisCommandFamily = analysisCommandFamily,
        rectRouteAuthority = rectRouteAuthority,
        rectGeometryAuthority = rectGeometryAuthority,
        rrectGeometryAuthority = rrectGeometryAuthority,
    )

    private fun GPUDrawSemanticPayload.CorePrimitive.withAnalyticClipState(
        scissorBounds: GPUPixelBounds,
        clipExecutionPlanIdentity: String,
    ) = GPUDrawSemanticPayload.CorePrimitive(
        payloadRef = payloadRef,
        sourceFamily = sourceFamily,
        geometry = geometry,
        premultipliedRgba = premultipliedRgba,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlanIdentity = clipExecutionPlanIdentity,
        blendPlanIdentity = blendPlanIdentity,
        frameProvenance = frameProvenance,
        coverageMode = coverageMode,
        analysisRecordId = analysisRecordId,
        analysisCommandFamily = analysisCommandFamily,
        rectRouteAuthority = rectRouteAuthority,
        rectGeometryAuthority = rectGeometryAuthority,
        rrectGeometryAuthority = rrectGeometryAuthority,
    )


    private fun packet(
        basePacket: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        preparedSemanticOverride: GPUDrawSemanticPayload.CorePrimitive?,
        direct: Boolean,
        clipStencilCompatible: Boolean,
        pathDepthStencilCompatible: Boolean,
        uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal?,
        analyticShape: GPUCorePrimitivePreparedAnalyticShape?,
        analyticShapeUniformSlabPlan: org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan?,
        analyticClipAuthority: GPUCorePrimitiveAnalyticClipAuthority.Accepted?,
        analyticUniformSlabPlan: org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan?,
        analyticUniformBytes: ByteArray?,
        analyticIntersectionAuthority: GPUCorePrimitiveAnalyticIntersectionAuthority.Accepted?,
        analyticIntersectionUniformSlabPlan: org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan?,
        analyticIntersectionUniformBytes: ByteArray?,
        coverageMaskUniformSlabSeal: GPUCorePrimitiveCoverageMaskUniformSlabSeal?,
        publicPipelineKeys: MutableMap<GPUCorePrimitiveRenderPipelineStructuralKey, GPURenderPipelineKey>,
    ): GPUDrawPacket {
        val clipExecutionPlan = requireNotNull(basePacket.clipExecutionPlan)
        val analyticScissor = analyticClipAuthority?.conservativeScissor
            ?: analyticIntersectionAuthority?.conservativeScissor
        val preparedSemantic = preparedSemanticOverride ?: analyticScissor?.let { scissor ->
            semantic.withAnalyticClipState(scissor, clipExecutionPlan.canonicalIdentity())
        } ?: semantic.withClipExecutionPlanIdentity(clipExecutionPlan.canonicalIdentity())
        val coverageMaskConsumerSlot = coverageMaskUniformSlabSeal?.consumerSlots
            ?.singleOrNull { it.packetId == basePacket.packetId }
        val baseStructuralPipelineKey = if (coverageMaskConsumerSlot != null) {
            coverageMaskConsumerSlot.structuralPipelineKey
        } else if (clipStencilCompatible) {
            val path = requireNotNull(
                (clipExecutionPlan as? GPUClipExecutionPlan.StencilCoverage)
                    ?.corePrimitiveClipStencilNativePathOrNull(),
            )
            corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(
                inverseFill = path.inverseFill,
                blendPlan = requireNotNull(basePacket.blendPlan),
            )
        } else {
            corePrimitiveRenderPipelineStructuralKey(
                preparedSemantic,
                clipExecutionPlan,
                requireNotNull(basePacket.blendPlan),
            )
        }
        val structuralPipelineKey = if (pathDepthStencilCompatible) {
            baseStructuralPipelineKey.copy(depthStencil = corePrimitiveDirectPathDepthStencilState())
        } else {
            baseStructuralPipelineKey
        }
        val renderPipelineKey = publicPipelineKeys.getOrPut(structuralPipelineKey) {
            structuralPipelineKey.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
        }
        val bindingLayoutHash = when {
            coverageMaskConsumerSlot != null -> coverageMaskConsumerSlot.bindingLayoutHash
            analyticShape != null -> CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH
            analyticClipAuthority != null -> CORE_PRIMITIVE_ANALYTIC_CLIP_BINDING_LAYOUT_HASH
            analyticIntersectionAuthority != null -> CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
            else -> CORE_PRIMITIVE_BINDING_LAYOUT_HASH
        }
        val analyticShapeUniformSeal = analyticShape?.let { authority ->
            require(preparedSemantic === authority.semantic) {
                "Analytic shape packet must retain the exact semantic used to build uniform80"
            }
            val plan = requireNotNull(analyticShapeUniformSlabPlan)
            val slotIndex = plan.slots.indexOfFirst {
                it.slotLabel == "analytic-shape-draw-${basePacket.commandIdValue}"
            }
            GPUCorePrimitiveAnalyticShapeUniformSeal(
                plan = plan,
                slotIndex = slotIndex,
                commandId = basePacket.commandIdValue,
                packetId = basePacket.packetId,
                semanticAuthority = authority.semanticAuthority,
                renderScissor = requireNotNull(authority.route.renderScissor),
                structuralPipelineKey = structuralPipelineKey,
                renderPipelineKey = renderPipelineKey,
                bindingLayoutHash = bindingLayoutHash,
                resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                payloadBytes = authority.uniformBytes,
            )
        }
        val analyticClipUniformSeal = analyticClipAuthority?.let { authority ->
            val plan = requireNotNull(analyticUniformSlabPlan)
            val bytes = requireNotNull(analyticUniformBytes)
            val slotIndex = plan.slots.indexOfFirst {
                it.slotLabel == "analytic-clip-draw-${basePacket.commandIdValue}"
            }
            GPUCorePrimitiveAnalyticClipUniformSeal(
                plan = plan,
                slotIndex = slotIndex,
                commandId = basePacket.commandIdValue,
                packetId = basePacket.packetId,
                clipCanonicalIdentity = clipExecutionPlan.canonicalIdentity(),
                clipType = authority.clipType,
                clipBounds = authority.bounds,
                clipRadii = authority.radii,
                antiAlias = authority.antiAlias,
                conservativeScissor = authority.conservativeScissor,
                structuralPipelineKey = structuralPipelineKey,
                renderPipelineKey = renderPipelineKey,
                bindingLayoutHash = bindingLayoutHash,
                resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                payloadBytes = bytes,
            )
        }
        val analyticIntersectionUniformSeal = analyticIntersectionAuthority?.let { authority ->
            val plan = requireNotNull(analyticIntersectionUniformSlabPlan)
            val bytes = requireNotNull(analyticIntersectionUniformBytes)
            val slotIndex = plan.slots.indexOfFirst {
                it.slotLabel == "analytic-intersection-draw-${basePacket.commandIdValue}"
            }
            GPUCorePrimitiveAnalyticIntersectionUniformSeal(
                plan = plan,
                slotIndex = slotIndex,
                commandId = basePacket.commandIdValue,
                packetId = basePacket.packetId,
                clipCanonicalIdentity = clipExecutionPlan.canonicalIdentity(),
                elements = authority.elements.map { element ->
                    GPUCorePrimitiveAnalyticIntersectionElementSeal(
                        element.clipType,
                        element.bounds,
                        element.packedRadii,
                        element.antiAlias,
                    )
                },
                conservativeScissor = authority.conservativeScissor,
                structuralPipelineKey = structuralPipelineKey,
                renderPipelineKey = renderPipelineKey,
                bindingLayoutHash = bindingLayoutHash,
                resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                payloadBytes = bytes,
            )
        }
        return GPUDrawPacket(
        packetId = basePacket.packetId,
        commandIdValue = basePacket.commandIdValue,
        analysisRecordId = basePacket.analysisRecordId,
        passId = when {
            pathDepthStencilCompatible -> "pass.core-primitive.path-stencil"
            direct -> "pass.core-primitive.direct"
            else -> basePacket.passId
        },
        layerId = basePacket.layerId,
        bindingListId = basePacket.bindingListId,
        insertionReasonCode = basePacket.insertionReasonCode,
        sortKey = basePacket.sortKey,
        sortKeyPreimage = basePacket.sortKeyPreimage,
        renderStepId = GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
        renderStepVersion = 1,
        role = basePacket.role,
        blendPlan = basePacket.blendPlan,
        renderPipelineKey = renderPipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        uniformSlot = preparedSemantic.payloadRef.uniformSlot,
        resourceSlot = basePacket.resourceSlot,
        semanticPayload = preparedSemantic,
        vertexSourceLabel = CORE_PRIMITIVE_VERTEX_SOURCE_LABEL,
        scissorBoundsHash = if (coverageMaskConsumerSlot == null) {
            corePrimitiveScissorAuthority(preparedSemantic.scissorBounds)
        } else {
            null
        },
        targetStateHash = CORE_PRIMITIVE_TARGET_STATE_HASH,
        originalPaintOrder = basePacket.originalPaintOrder,
        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        frameProvenance = basePacket.frameProvenance,
        clipCoveragePlan = basePacket.clipCoveragePlan,
        clipExecutionPlan = clipExecutionPlan,
        diagnostics = basePacket.diagnostics,
    ).attachCorePrimitivePreparedAuthority(
        GPUCorePrimitivePreparedPacketAuthority(
            structuralPipelineKey = structuralPipelineKey,
            renderPipelineKey = renderPipelineKey,
            uniformSlabSeal = uniformSlabSeal.takeIf {
                direct && analyticShape == null && analyticClipAuthority == null &&
                    analyticIntersectionAuthority == null
            },
            analyticShapeUniformSeal = analyticShapeUniformSeal,
            analyticClipUniformSeal = analyticClipUniformSeal,
            analyticIntersectionUniformSeal = analyticIntersectionUniformSeal,
            coverageMaskUniformSlabSeal = coverageMaskUniformSlabSeal.takeIf {
                coverageMaskConsumerSlot != null
            },
        ),
    )
    }

    private fun dependency(from: GPUTaskID, to: GPUTaskID, index: Int) = GPUTaskDependency(
        from,
        to,
        "prepared-scene-order",
        GPUTaskUseToken("prepared-core-primitive.$index"),
        "preserve.prepared-scene.order",
    )

    private fun refused(code: String, message: String) = GPUCorePrimitivePreparedFrameResult.Refused(
        GPUDiagnostic(
            GPUDiagnosticCode(code),
            GPUDiagnosticDomain.Recording,
            GPUDiagnosticSeverity.Error,
            message,
        ),
    )
}
