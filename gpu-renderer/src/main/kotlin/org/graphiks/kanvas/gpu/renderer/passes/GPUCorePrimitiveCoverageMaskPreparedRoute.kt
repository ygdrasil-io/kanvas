package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

/** Color attachment authority retained without a native texture or view handle. */
internal data class GPUCorePrimitiveCoverageMaskAttachmentAuthority(
    val logicalReference: String,
    val width: Int,
    val height: Int,
    val format: GPUCorePrimitiveCoverageMaskAttachmentFormat,
    val sampleCount: Int,
    val deviceGeneration: GPUDeviceGenerationID,
    val resourceGeneration: Long,
) {
    init {
        require(logicalReference.isNotBlank() && width > 0 && height > 0 && sampleCount > 0 &&
            resourceGeneration >= 0L
        ) { "Coverage-mask attachment authority requires one typed generated color target" }
    }
}

internal enum class GPUCorePrimitiveCoverageMaskAttachmentFormat {
    Rgba8Unorm,
    Bgra8Unorm,
}

/** Exact immutable geometry subset executable by the direct mask-consumer shader. */
internal sealed interface GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot {
    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot

    class DirectTriangles(
        vertices: List<Float>,
        indices: List<Int>,
        sourceContourStarts: List<Int>,
        val sourceVertexCount: Int,
        val coverBounds: GPUPixelBounds,
        val fillRule: GPUCorePrimitiveFillRule,
    ) : GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot {
        val vertices: List<Float> = immutableList(vertices)
        val indices: List<Int> = immutableList(indices)
        val sourceContourStarts: List<Int> = immutableList(sourceContourStarts)

        override fun equals(other: Any?): Boolean = this === other ||
            other is DirectTriangles && vertices == other.vertices && indices == other.indices &&
            sourceContourStarts == other.sourceContourStarts &&
            sourceVertexCount == other.sourceVertexCount && coverBounds == other.coverBounds &&
            fillRule == other.fillRule

        override fun hashCode(): Int = listOf(
            vertices,
            indices,
            sourceContourStarts,
            sourceVertexCount,
            coverBounds,
            fillRule,
        ).hashCode()
    }
}

/** One typed CorePrimitive consumer observed at the pure snapshot boundary. */
internal data class GPUCorePrimitiveCoverageMaskConsumerInput(
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val sourceOrder: Int,
    val semanticCanonicalIdentity: String,
    val coverageMode: GPUCorePrimitiveCoverageMode,
    val blendPlan: GPUBlendPlan,
    val orderingToken: GPUClipOrderingToken,
    val packetRole: GPUDrawPacketRole,
    val geometry: GPUCorePrimitiveGeometry,
) {
    init {
        require(commandId >= 0 && sourceOrder >= 0 && semanticCanonicalIdentity.isNotBlank()) {
            "Coverage-mask consumer input requires stable CorePrimitive semantic and order authority"
        }
    }
}

/** Immutable current-state snapshot revalidated both before candidate capture and final sealing. */
internal class GPUCorePrimitiveCoverageMaskPreparedRouteRequest(
    val plan: GPUClipExecutionPlan.CoverageMask,
    consumers: List<GPUCorePrimitiveCoverageMaskConsumerInput>,
    val attachment: GPUCorePrimitiveCoverageMaskAttachmentAuthority,
) {
    val consumers: List<GPUCorePrimitiveCoverageMaskConsumerInput> = immutableList(consumers)

    fun copy(
        plan: GPUClipExecutionPlan.CoverageMask = this.plan,
        consumers: List<GPUCorePrimitiveCoverageMaskConsumerInput> = this.consumers,
        attachment: GPUCorePrimitiveCoverageMaskAttachmentAuthority = this.attachment,
    ) = GPUCorePrimitiveCoverageMaskPreparedRouteRequest(plan, consumers, attachment)
}

internal data class GPUCorePrimitiveCoverageMaskPreparedProducerSnapshot(
    val sourceOrder: Int,
    val geometry: GPUClipExecutionGeometry,
    val combine: GPUClipMaskCombine,
    val antiAlias: Boolean,
    val structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
)

internal data class GPUCorePrimitiveCoverageMaskPreparedConsumerSnapshot(
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val sourceOrder: Int,
    val semanticCanonicalIdentity: String,
    val packetRole: GPUDrawPacketRole,
    val geometry: GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot,
    val coverageMode: GPUCorePrimitiveCoverageMode,
    val blendCanonicalIdentity: String,
    val orderingToken: GPUClipOrderingToken,
    val structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
)

/** Recording-time evidence containing structural keys plus immutable dynamic-value snapshots. */
internal class GPUCorePrimitiveCoverageMaskPreparedCandidate internal constructor(
    val contentKey: String,
    val planCanonicalIdentity: String,
    val bounds: GPUPixelBounds,
    val orderingToken: GPUClipOrderingToken,
    producers: List<GPUCorePrimitiveCoverageMaskPreparedProducerSnapshot>,
    consumers: List<GPUCorePrimitiveCoverageMaskPreparedConsumerSnapshot>,
    val attachment: GPUCorePrimitiveCoverageMaskAttachmentAuthority,
) {
    val producers: List<GPUCorePrimitiveCoverageMaskPreparedProducerSnapshot> = immutableList(producers)
    val consumers: List<GPUCorePrimitiveCoverageMaskPreparedConsumerSnapshot> = immutableList(consumers)
}

internal sealed interface GPUCorePrimitiveCoverageMaskPreparedCandidateDecision {
    data class Accepted(
        val candidate: GPUCorePrimitiveCoverageMaskPreparedCandidate,
    ) : GPUCorePrimitiveCoverageMaskPreparedCandidateDecision

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUCorePrimitiveCoverageMaskPreparedCandidateDecision
}

internal sealed interface GPUCorePrimitiveCoverageMaskPreparedRoute {
    class Accepted internal constructor(
        val contentKey: String,
        val planCanonicalIdentity: String,
        val bounds: GPUPixelBounds,
        val orderingToken: GPUClipOrderingToken,
        producers: List<GPUCorePrimitiveCoverageMaskPreparedProducerSnapshot>,
        consumers: List<GPUCorePrimitiveCoverageMaskPreparedConsumerSnapshot>,
        val attachment: GPUCorePrimitiveCoverageMaskAttachmentAuthority,
    ) : GPUCorePrimitiveCoverageMaskPreparedRoute {
        val producers: List<GPUCorePrimitiveCoverageMaskPreparedProducerSnapshot> = immutableList(producers)
        val consumers: List<GPUCorePrimitiveCoverageMaskPreparedConsumerSnapshot> = immutableList(consumers)
    }

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUCorePrimitiveCoverageMaskPreparedRoute
}

/** Captures one handle-free candidate only after the complete color-only route has been validated. */
internal fun snapshotGPUCorePrimitiveCoverageMaskPreparedCandidate(
    request: GPUCorePrimitiveCoverageMaskPreparedRouteRequest,
): GPUCorePrimitiveCoverageMaskPreparedCandidateDecision = when (val validation = request.validate()) {
    is CoverageMaskRequestValidation.Refused ->
        GPUCorePrimitiveCoverageMaskPreparedCandidateDecision.Refused(
            validation.code,
            validation.message,
        )
    is CoverageMaskRequestValidation.Accepted ->
        GPUCorePrimitiveCoverageMaskPreparedCandidateDecision.Accepted(
            validation.toCandidate(request),
        )
}

/** Revalidates all live facts and seals exactly the candidate that was snapshotted. */
internal fun sealGPUCorePrimitiveCoverageMaskPreparedRoute(
    candidate: GPUCorePrimitiveCoverageMaskPreparedCandidate,
    request: GPUCorePrimitiveCoverageMaskPreparedRouteRequest,
): GPUCorePrimitiveCoverageMaskPreparedRoute {
    val validation = request.validate()
    if (validation is CoverageMaskRequestValidation.Refused) {
        return GPUCorePrimitiveCoverageMaskPreparedRoute.Refused(validation.code, validation.message)
    }
    validation as CoverageMaskRequestValidation.Accepted
    val current = validation.toCandidate(request)
    if (candidate.attachment.deviceGeneration != current.attachment.deviceGeneration ||
        candidate.attachment.resourceGeneration != current.attachment.resourceGeneration
    ) return routeRefused(
        "invalid.prepared-core-primitive.coverage-mask.stale-authority",
        "The coverage-mask color attachment generation changed after candidate capture.",
    )
    if (!candidate.matches(current)) return routeRefused(
        "invalid.prepared-core-primitive.coverage-mask.substituted",
        "Coverage-mask plan, dynamic payload, ordering, structural key, or attachment authority was substituted.",
    )
    return GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted(
        contentKey = candidate.contentKey,
        planCanonicalIdentity = candidate.planCanonicalIdentity,
        bounds = candidate.bounds,
        orderingToken = candidate.orderingToken,
        producers = candidate.producers,
        consumers = candidate.consumers,
        attachment = candidate.attachment,
    )
}

private sealed interface CoverageMaskRequestValidation {
    class Accepted(
        val producers: List<GPUCorePrimitiveCoverageMaskPreparedProducerSnapshot>,
        val consumers: List<GPUCorePrimitiveCoverageMaskPreparedConsumerSnapshot>,
    ) : CoverageMaskRequestValidation

    data class Refused(val code: String, val message: String) : CoverageMaskRequestValidation
}

private fun GPUCorePrimitiveCoverageMaskPreparedRouteRequest.validate(): CoverageMaskRequestValidation {
    if (plan.depthStencilRequired) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.depth-stencil",
        "B3.3d accepts only the color-only coverage-mask route.",
    )
    if (plan.sampleCount != 1 || attachment.sampleCount != 1) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.msaa",
        "B3.3d coverage masks are single-sample.",
    )
    if (attachment.format != GPUCorePrimitiveCoverageMaskAttachmentFormat.Rgba8Unorm) {
        return requestRefused(
            "unsupported.prepared-core-primitive.coverage-mask.target-format",
            "B3.3d coverage masks require one RGBA8unorm color target.",
        )
    }
    val fullTarget = GPUPixelBounds(0, 0, attachment.width, attachment.height)
    if (plan.bounds != fullTarget) return requestRefused(
        "invalid.prepared-core-primitive.coverage-mask.full-target",
        "The coverage-mask allocation must exactly cover the logical target.",
    )
    if (plan.consumer.sampling != GPUClipMaskSampling.Nearest) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.sampling",
        "B3.3d coverage-mask consumers require nearest sampling.",
    )
    if (plan.producers.any { it.geometry is GPUClipExecutionGeometry.Path }) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.producer-path",
        "B3.3d coverage-mask producers accept only Rect and RRect geometry.",
    )
    if (plan.producers.any { it.geometry !is GPUClipExecutionGeometry.Rect &&
            it.geometry !is GPUClipExecutionGeometry.RRect
        }
    ) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.producer-geometry",
        "B3.3d coverage-mask producer geometry is not supported.",
    )
    if (plan.producers.any { it.antiAlias }) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.anti-alias",
        "B3.3d coverage-mask producers are explicitly non-AA.",
    )
    val rrectClassifications = plan.producers.mapNotNull { producer ->
        (producer.geometry as? GPUClipExecutionGeometry.RRect)?.coverageMaskProducerClassification()
    }
    if (CoverageMaskRRectProducerClassification.MixedZeroRefused in rrectClassifications) {
        return requestRefused(
            "unsupported.prepared-core-primitive.coverage-mask.rrect-mixed-zero-radii",
            "B3.3d RRect producers require either eight zero radii or eight strictly positive radii.",
        )
    }
    if (CoverageMaskRRectProducerClassification.SubEpsilonRefused in rrectClassifications) {
        return requestRefused(
            "unsupported.prepared-core-primitive.coverage-mask.rrect-sub-epsilon-radii",
            "B3.3d RRect producer radii must be zero or at least 0.0001.",
        )
    }
    if (CoverageMaskRRectProducerClassification.OverHalfRefused in rrectClassifications) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.rrect-radii",
        "B3.3d RRect producer radii must not exceed half of their exact bounds.",
    )
    if (!plan.producers.zipWithNext().all { (left, right) -> left.sourceOrder < right.sourceOrder }) {
        return requestRefused(
            "invalid.prepared-core-primitive.coverage-mask.ordering",
            "Coverage-mask producers must retain strict source order.",
        )
    }
    if (consumers.size < 2) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.consumer-count",
        "B3.3d requires at least two typed CorePrimitive consumers.",
    )
    if (consumers.map { it.packetId }.distinct().size != consumers.size ||
        consumers.map { it.commandId }.distinct().size != consumers.size ||
        !consumers.zipWithNext().all { (left, right) -> left.sourceOrder < right.sourceOrder }
    ) return requestRefused(
        "invalid.prepared-core-primitive.coverage-mask.ordering",
        "Coverage-mask consumers must retain unique identities and strict source order.",
    )
    if (consumers.any { it.orderingToken != plan.orderingToken }) return requestRefused(
        "invalid.prepared-core-primitive.coverage-mask.ordering-authority",
        "Every CorePrimitive consumer must retain the exact mask ordering token.",
    )
    if (consumers.any { it.packetRole != GPUDrawPacketRole.Shading }) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.consumer-role",
        "B3.3d accepts only Shading CorePrimitive packets as mask consumers.",
    )
    val consumerGeometry = consumers.map { it.geometry.coverageMaskDirectSnapshotOrNull() }
    if (consumerGeometry.any { it == null }) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.consumer-geometry",
        "B3.3d accepts only Rect or non-inverse unstroked DirectTriangles consumers.",
    )
    if (consumers.any { it.coverageMode != GPUCorePrimitiveCoverageMode.FullOrScissor }) {
        return requestRefused(
            "unsupported.prepared-core-primitive.coverage-mask.consumer-coverage",
            "AA and stencil consumer coverage remain outside B3.3d.",
        )
    }
    if (consumers.any {
            it.blendPlan.destinationReadRequirement != GPUBlendDestinationReadRequirement.None
        }
    ) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.destination-read",
        "Destination-read blends cannot consume the single sampled coverage mask.",
    )
    if (consumers.any { !it.blendPlan.isCanonicalPremulSrcOver() }) return requestRefused(
        "unsupported.prepared-core-primitive.coverage-mask.blend",
        "B3.3d consumers require the exact fixed-function premultiplied SrcOver blend.",
    )

    val producerSnapshots = plan.producers.map { producer ->
        GPUCorePrimitiveCoverageMaskPreparedProducerSnapshot(
            sourceOrder = producer.sourceOrder,
            geometry = producer.geometry,
            combine = producer.combine,
            antiAlias = producer.antiAlias,
            structuralKey = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
                geometry = when (producer.geometry) {
                    is GPUClipExecutionGeometry.Rect ->
                        GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
                    is GPUClipExecutionGeometry.RRect -> when (
                        producer.geometry.coverageMaskProducerClassification()
                    ) {
                        CoverageMaskRRectProducerClassification.RectDegenerate ->
                            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect
                        CoverageMaskRRectProducerClassification.RRectSupported ->
                            GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.RRect
                        CoverageMaskRRectProducerClassification.MixedZeroRefused,
                        CoverageMaskRRectProducerClassification.SubEpsilonRefused,
                        CoverageMaskRRectProducerClassification.OverHalfRefused,
                        -> error("Validated above")
                    }
                    is GPUClipExecutionGeometry.Path -> error("Validated above")
                },
                combine = producer.combine,
            ),
        )
    }
    val consumerSnapshots = consumers.zip(consumerGeometry).map { (consumer, geometry) ->
        GPUCorePrimitiveCoverageMaskPreparedConsumerSnapshot(
            packetId = consumer.packetId,
            commandId = consumer.commandId,
            sourceOrder = consumer.sourceOrder,
            semanticCanonicalIdentity = consumer.semanticCanonicalIdentity,
            packetRole = consumer.packetRole,
            geometry = requireNotNull(geometry),
            coverageMode = consumer.coverageMode,
            blendCanonicalIdentity = consumer.blendPlan.canonicalIdentity(),
            orderingToken = consumer.orderingToken,
            structuralKey = corePrimitiveCoverageMaskConsumerRenderPipelineStructuralKey(
                consumer.blendPlan,
            ),
        )
    }
    return CoverageMaskRequestValidation.Accepted(producerSnapshots, consumerSnapshots)
}

private fun CoverageMaskRequestValidation.Accepted.toCandidate(
    request: GPUCorePrimitiveCoverageMaskPreparedRouteRequest,
) = GPUCorePrimitiveCoverageMaskPreparedCandidate(
    contentKey = request.plan.contentKey,
    planCanonicalIdentity = request.plan.canonicalIdentity(),
    bounds = request.plan.bounds,
    orderingToken = request.plan.orderingToken,
    producers = producers,
    consumers = consumers,
    attachment = request.attachment,
)

private fun GPUCorePrimitiveCoverageMaskPreparedCandidate.matches(
    other: GPUCorePrimitiveCoverageMaskPreparedCandidate,
): Boolean = contentKey == other.contentKey &&
    planCanonicalIdentity == other.planCanonicalIdentity && bounds == other.bounds &&
    orderingToken == other.orderingToken && producers == other.producers &&
    consumers == other.consumers && attachment == other.attachment

private fun GPUBlendPlan.isCanonicalPremulSrcOver(): Boolean {
    val fixed = this as? GPUBlendPlan.FixedFunctionBlend ?: return false
    return fixed.mode == GPUBlendMode.SRC_OVER &&
        fixed.sourceCoverageEncoding == GPUSourceCoverageEncoding.None &&
        fixed.state.isCanonicalPremulSrcOver()
}

private fun GPUFixedFunctionBlendState.isCanonicalPremulSrcOver(): Boolean =
    color.sourceFactor == "one" && color.destinationFactor == "one-minus-src-alpha" &&
        color.operation == "add" && alpha.sourceFactor == "one" &&
        alpha.destinationFactor == "one-minus-src-alpha" && alpha.operation == "add" &&
        writeMask == "rgba"

private fun GPUCorePrimitiveGeometry.coverageMaskDirectSnapshotOrNull():
    GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot? = when (this) {
    is GPUCorePrimitiveGeometry.Rect -> if (
        listOf(left, top, right, bottom).all(Float::isFinite) && left < right && top < bottom
    ) {
        GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.Rect(left, top, right, bottom)
    } else {
        null
    }
    is GPUCorePrimitiveGeometry.RRect -> null
    is GPUCorePrimitiveGeometry.TriangulatedPath -> if (
        geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles && !inverseFill &&
        strokeStyle == null
    ) {
        GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.DirectTriangles(
            vertices = vertices,
            indices = indices,
            sourceContourStarts = sourceContourStarts,
            sourceVertexCount = sourceVertexCount,
            coverBounds = coverBounds,
            fillRule = fillRule,
        )
    } else {
        null
    }
}

private enum class CoverageMaskRRectProducerClassification {
    RectDegenerate,
    RRectSupported,
    MixedZeroRefused,
    SubEpsilonRefused,
    OverHalfRefused,
}

private const val COVERAGE_MASK_RRECT_MIN_RADIUS = 0.0001f

private fun GPUClipExecutionGeometry.RRect.coverageMaskProducerClassification():
    CoverageMaskRRectProducerClassification {
    if (radii.all { it == 0f }) return CoverageMaskRRectProducerClassification.RectDegenerate
    if (radii.any { it == 0f }) return CoverageMaskRRectProducerClassification.MixedZeroRefused
    if (radii.any { it in 0f..<COVERAGE_MASK_RRECT_MIN_RADIUS }) {
        return CoverageMaskRRectProducerClassification.SubEpsilonRefused
    }
    val halfWidth = (bounds.right - bounds.left) * 0.5f
    val halfHeight = (bounds.bottom - bounds.top) * 0.5f
    return if (radii.chunked(2).all { (rx, ry) ->
            rx >= COVERAGE_MASK_RRECT_MIN_RADIUS &&
                ry >= COVERAGE_MASK_RRECT_MIN_RADIUS &&
                rx <= halfWidth &&
                ry <= halfHeight
        }
    ) {
        CoverageMaskRRectProducerClassification.RRectSupported
    } else {
        CoverageMaskRRectProducerClassification.OverHalfRefused
    }
}

private fun requestRefused(code: String, message: String) =
    CoverageMaskRequestValidation.Refused(code, message)

private fun routeRefused(code: String, message: String) =
    GPUCorePrimitiveCoverageMaskPreparedRoute.Refused(code, message)
