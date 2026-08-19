package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode

/** Logical attachment identity carried by the pure route; it deliberately contains no native handle. */
internal data class GPUCorePrimitiveClipStencilAttachmentAuthority(
    val logicalReference: String,
    val width: Int,
    val height: Int,
    val format: GPUCorePrimitiveClipStencilAttachmentFormat,
    val sampleCount: Int,
    val deviceGeneration: GPUDeviceGenerationID,
    val resourceGeneration: Long,
)

internal enum class GPUCorePrimitiveClipStencilAttachmentFormat {
    Depth24PlusStencil8,
}

/** One prospective draw consuming the already-produced clip stencil. */
internal data class GPUCorePrimitiveClipStencilConsumerInput(
    val commandId: Int,
    val sourceOrder: Int,
    val geometry: GPUCorePrimitiveGeometry,
    val coverageMode: GPUCorePrimitiveCoverageMode,
    val blendPlan: GPUBlendPlan,
    val inverseFill: Boolean,
    val stencilReference: UInt,
    val atomicGroup: GPUClipAtomicGroupID,
    val orderingToken: GPUClipOrderingToken,
    val scissor: GPUPixelBounds?,
    val attachment: GPUCorePrimitiveClipStencilAttachmentAuthority,
    val isLastConsumer: Boolean,
)

/** Mutable-boundary path facts revalidated by the pure seal before device-to-NDC conversion. */
internal class GPUCorePrimitiveClipStencilProducerGeometryAuthority(
    vertices: List<Float>,
    contourStarts: List<Int>,
) {
    val vertices: List<Float> = immutableList(vertices)
    val contourStarts: List<Int> = immutableList(contourStarts)
}

/** Snapshot boundary for pure native clip-stencil route validation. */
internal class GPUCorePrimitiveClipStencilNativeRouteRequest(
    clipArtifacts: List<GPUClipExecutionPlan>,
    consumers: List<GPUCorePrimitiveClipStencilConsumerInput>,
    val producerGeometry: GPUCorePrimitiveClipStencilProducerGeometryAuthority,
    val producerAttachment: GPUCorePrimitiveClipStencilAttachmentAuthority,
    val producerAntiAlias: Boolean,
    val expectedLastConsumerCommandId: Int,
) {
    val clipArtifacts: List<GPUClipExecutionPlan> = immutableList(clipArtifacts)
    val consumers: List<GPUCorePrimitiveClipStencilConsumerInput> = immutableList(consumers)
}

internal sealed interface GPUCorePrimitiveClipStencilNativeRoute {
    class Accepted(
        val producer: ProducerSeal,
        consumers: List<ConsumerSeal>,
        val attachment: GPUCorePrimitiveClipStencilAttachmentAuthority,
        val stencilReference: UInt,
        val atomicGroup: GPUClipAtomicGroupID,
        val orderingToken: GPUClipOrderingToken,
        val lastConsumerCommandId: Int,
    ) : GPUCorePrimitiveClipStencilNativeRoute {
        val consumers: List<ConsumerSeal> = immutableList(consumers)
    }

    data class Refused(val code: String, val message: String) : GPUCorePrimitiveClipStencilNativeRoute

    class ProducerSeal(
        ndcVertices: List<Float>,
        contourStarts: List<Int>,
        val fillRule: GPUClipFillRule,
        val scissor: GPUPixelBounds?,
        val contentKey: String,
        val planCanonicalIdentity: String,
        val structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    ) {
        val ndcVertices: List<Float> = immutableList(ndcVertices)
        val contourStarts: List<Int> = immutableList(contourStarts)
    }

    data class ConsumerSeal(
        val commandId: Int,
        val sourceOrder: Int,
        val geometry: GPUCorePrimitiveGeometry,
        val scissor: GPUPixelBounds?,
        val structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    )
}

/**
 * Pure authority seal for the deliberately small native path-clip lane.
 *
 * This function allocates no WebGPU resources and does not perform builder, preflight, packing, or
 * multi-pass materialization work.
 */
internal fun sealGPUCorePrimitiveClipStencilNativeRoute(
    request: GPUCorePrimitiveClipStencilNativeRouteRequest,
): GPUCorePrimitiveClipStencilNativeRoute {
    if (request.clipArtifacts.size != 1) return refused(
        "unsupported.native-core-primitive.clip-stencil.multiple-artifacts",
        "The bounded route accepts exactly one clip-stencil artifact.",
    )
    val artifact = request.clipArtifacts.single()
    if (artifact is GPUClipExecutionPlan.CoverageMask) return refused(
        "unsupported.native-core-primitive.clip-stencil.mask",
        "Coverage-mask clips remain outside the pure clip-stencil route.",
    )
    val stencil = artifact as? GPUClipExecutionPlan.StencilCoverage ?: return refused(
        "unsupported.native-core-primitive.clip-stencil.plan",
        "The bounded route requires one stencil-coverage plan.",
    )
    if (stencil.sampleCount !in setOf(1, 4)) return refused(
        "unsupported.native-core-primitive.clip-stencil.msaa",
        "The bounded clip-stencil route supports exactly one or four samples.",
    )
    if (request.producerAntiAlias != (stencil.sampleCount == 4)) return refused(
        "unsupported.native-core-primitive.clip-stencil.anti-alias",
        "Clip-stencil producer AA authority must match the exact one or four sample plan.",
    )
    val path = stencil.producer.geometry as? GPUClipExecutionGeometry.Path ?: return refused(
        "invalid.native-core-primitive.clip-stencil.producer-geometry",
        "The clip-stencil producer requires one validated path geometry.",
    )
    if (!stencil.producer.hasExactNativeState(path.fillRule)) return refused(
        "invalid.native-core-primitive.clip-stencil.producer-state",
        "The producer state does not match the real non-AA path mapper authority.",
    )
    if (!request.producerGeometry.hasValidContours() ||
        request.producerGeometry.vertices != path.vertices ||
        request.producerGeometry.contourStarts != path.contourStarts
    ) return refused(
        "invalid.native-core-primitive.clip-stencil.producer-geometry",
        "The producer requires finite triangle-capable contours matching the exact clip plan.",
    )
    if (request.consumers.isEmpty()) return refused(
        "invalid.native-core-primitive.clip-stencil.last-consumer",
        "The clip-stencil route requires at least one consumer.",
    )
    if (request.consumers.any { it.commandId < 0 || it.sourceOrder < 0 } ||
        request.consumers.map { it.commandId }.distinct().size != request.consumers.size ||
        !request.consumers.zipWithNext().all { (left, right) -> left.sourceOrder < right.sourceOrder }
    ) {
        return refused(
            "invalid.native-core-primitive.clip-stencil.ordering",
            "Clip-stencil consumers must retain strict source order.",
        )
    }
    val declaredLast = request.consumers.filter(GPUCorePrimitiveClipStencilConsumerInput::isLastConsumer)
    if (declaredLast.size != 1 || declaredLast.single() !== request.consumers.last() ||
        declaredLast.single().commandId != request.expectedLastConsumerCommandId
    ) return refused(
        "invalid.native-core-primitive.clip-stencil.last-consumer",
        "Exactly the final ordered consumer must carry the last-consumer authority.",
    )

    val attachment = request.producerAttachment
    if (!attachment.isValidFor(stencil) || request.consumers.any { it.attachment != attachment }) return refused(
        "invalid.native-core-primitive.clip-stencil.attachment-authority",
        "All consumers must share one exact logical D24S8 attachment authority.",
    )
    if (!stencil.producer.scissor.isValidForAttachment(attachment)) return refused(
        "invalid.native-core-primitive.clip-stencil.producer-scissor",
        "The producer scissor must be non-empty and contained by the stencil attachment.",
    )
    if (request.consumers.any { !it.scissor.isValidForAttachment(attachment) }) return refused(
        "invalid.native-core-primitive.clip-stencil.consumer-scissor",
        "Every consumer scissor must be non-empty and contained by the stencil attachment.",
    )
    if (request.consumers.any { it.atomicGroup != stencil.atomicGroup }) return refused(
        "invalid.native-core-primitive.clip-stencil.atomic-authority",
        "All consumers must retain the producer atomic group.",
    )
    if (request.consumers.any { it.orderingToken != stencil.orderingToken }) return refused(
        "invalid.native-core-primitive.clip-stencil.ordering-authority",
        "All consumers must retain the producer ordering token.",
    )
    if (stencil.producer.reference != 0u || stencil.consumer.reference != 0u ||
        stencil.producer.reference != stencil.consumer.reference ||
        request.consumers.any { it.stencilReference != stencil.producer.reference }
    ) return refused(
        "invalid.native-core-primitive.clip-stencil.reference-authority",
        "Winding and even-odd clip stencil require the exact zero comparison reference.",
    )
    if (!stencil.consumer.hasExactNativeState(path.inverseFill)) return refused(
        "invalid.native-core-primitive.clip-stencil.consumer-state",
        "The consumer state must be read-only NotEqual/Equal with Keep operations.",
    )
    val producerKey = corePrimitiveClipStencilProducerRenderPipelineStructuralKey(
        path.fillRule,
        stencil.sampleCount,
    )
    val ndcVertices = corePrimitiveClipStencilNdcVertices(
        request.producerGeometry.vertices,
        attachment.width,
        attachment.height,
    ) ?: return refused(
        "invalid.native-core-primitive.clip-stencil.producer-geometry",
        "Producer geometry could not be sealed as finite NDC vertices.",
    )

    val consumerSeals = mutableListOf<GPUCorePrimitiveClipStencilNativeRoute.ConsumerSeal>()
    request.consumers.forEach { consumer ->
        if (consumer.coverageMode != GPUCorePrimitiveCoverageMode.FullOrScissor) return refused(
            "unsupported.native-core-primitive.clip-stencil.consumer-coverage",
            "AA and stencil-AA consumers remain outside the bounded route.",
        )
        if (!consumer.geometry.isDirectConsumerGeometry()) return refused(
            "unsupported.native-core-primitive.clip-stencil.consumer-geometry",
            "Only Rect and DirectTriangles consumers are accepted.",
        )
        if (consumer.inverseFill != path.inverseFill || consumer.scissor != stencil.consumer.scissor) return refused(
            "invalid.native-core-primitive.clip-stencil.consumer-authority",
            "Consumer fill and scissor facts must match the clip plan.",
        )
        if (consumer.blendPlan.destinationReadRequirement != GPUBlendDestinationReadRequirement.None) {
            return refused(
                "unsupported.native-core-primitive.clip-stencil.destination-read",
                "Destination-read blends remain outside the single-pass clip-stencil route.",
            )
        }
        val consumerKey = corePrimitiveClipStencilConsumerRenderPipelineStructuralKey(
            inverseFill = consumer.inverseFill,
            blendPlan = consumer.blendPlan,
            sampleCount = stencil.sampleCount,
        )
        if (!consumerKey.blend.isCanonicalPremulSrcOver()) return refused(
            "unsupported.native-core-primitive.clip-stencil.blend",
            "The consumer blend has no exact bounded native program.",
        )
        consumerSeals += GPUCorePrimitiveClipStencilNativeRoute.ConsumerSeal(
            commandId = consumer.commandId,
            sourceOrder = consumer.sourceOrder,
            geometry = consumer.geometry,
            scissor = consumer.scissor,
            structuralKey = consumerKey,
        )
    }

    return GPUCorePrimitiveClipStencilNativeRoute.Accepted(
        producer = GPUCorePrimitiveClipStencilNativeRoute.ProducerSeal(
            ndcVertices = ndcVertices,
            contourStarts = request.producerGeometry.contourStarts,
            fillRule = path.fillRule,
            scissor = stencil.producer.scissor,
            contentKey = stencil.contentKey,
            planCanonicalIdentity = stencil.canonicalIdentity(),
            structuralKey = producerKey,
        ),
        consumers = consumerSeals,
        attachment = attachment,
        stencilReference = stencil.producer.reference,
        atomicGroup = stencil.atomicGroup,
        orderingToken = stencil.orderingToken,
        lastConsumerCommandId = request.expectedLastConsumerCommandId,
    )
}

internal fun GPUClipExecutionPlan.StencilCoverage.corePrimitiveClipStencilNativePathOrNull():
    GPUClipExecutionGeometry.Path? {
    if (sampleCount !in setOf(1, 4)) return null
    val path = producer.geometry as? GPUClipExecutionGeometry.Path ?: return null
    if (!producer.hasExactNativeState(path.fillRule) ||
        !consumer.hasExactNativeState(path.inverseFill) || producer.reference != 0u ||
        consumer.reference != 0u || !path.hasTriangleContours()
    ) return null
    return path
}

private fun org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan.hasExactNativeState(
    pathFillRule: GPUClipFillRule,
): Boolean {
    val expected = when (pathFillRule) {
        GPUClipFillRule.Winding ->
            GPUClipStencilOperation.IncrementWrap to GPUClipStencilOperation.DecrementWrap
        GPUClipFillRule.EvenOdd -> GPUClipStencilOperation.Invert to GPUClipStencilOperation.Invert
    }
    return fillRule == pathFillRule && compare == GPUClipStencilCompare.Always &&
        frontPassOperation == expected.first && backPassOperation == expected.second &&
        failOperation == GPUClipStencilOperation.Keep &&
        depthFailOperation == GPUClipStencilOperation.Keep &&
        readMask == 0xffu && writeMask == 0xffu &&
        loadOperation == GPUClipStencilLoadOperation.Clear &&
        storeOperation == GPUClipStencilStoreOperation.Store && clearValue == 0u
}

private fun org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan.hasExactNativeState(
    inverseFill: Boolean,
): Boolean = compare == (if (inverseFill) GPUClipStencilCompare.Equal else GPUClipStencilCompare.NotEqual) &&
    passOperation == GPUClipStencilOperation.Keep &&
    failOperation == GPUClipStencilOperation.Keep &&
    depthFailOperation == GPUClipStencilOperation.Keep &&
    readMask == 0xffu && writeMask == 0u &&
    loadOperation == GPUClipStencilLoadOperation.Load &&
    storeOperation == GPUClipStencilStoreOperation.Store && clearValue == null

private fun GPUCorePrimitiveClipStencilAttachmentAuthority.isValidFor(
    stencil: GPUClipExecutionPlan.StencilCoverage,
): Boolean = logicalReference.isNotBlank() && width > 0 && height > 0 &&
    format == GPUCorePrimitiveClipStencilAttachmentFormat.Depth24PlusStencil8 &&
    sampleCount in setOf(1, 4) &&
    resourceGeneration >= 0L && !stencil.bounds.isEmpty &&
    stencil.bounds.left >= 0 && stencil.bounds.top >= 0 &&
    stencil.bounds.right <= width && stencil.bounds.bottom <= height &&
    stencil.sampleCount == sampleCount

private fun GPUPixelBounds?.isValidForAttachment(
    attachment: GPUCorePrimitiveClipStencilAttachmentAuthority,
): Boolean = this == null || (!isEmpty && left >= 0 && top >= 0 &&
    right <= attachment.width && bottom <= attachment.height)

private fun GPUCorePrimitiveGeometry.isDirectConsumerGeometry(): Boolean = when (this) {
    is GPUCorePrimitiveGeometry.Rect -> true
    is GPUCorePrimitiveGeometry.TriangulatedPath ->
        geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles
    is GPUCorePrimitiveGeometry.RRect -> false
}

private fun GPUCorePrimitiveRenderPipelineStructuralKey.Blend.isCanonicalPremulSrcOver(): Boolean {
    val fixed = this as? GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed ?: return false
    return fixed.mode == GPUBlendMode.SRC_OVER &&
        fixed.sourceCoverage == GPUSourceCoverageEncoding.None &&
        fixed.state.color.sourceFactor == "one" &&
        fixed.state.color.destinationFactor == "one-minus-src-alpha" &&
        fixed.state.color.operation == "add" &&
        fixed.state.alpha.sourceFactor == "one" &&
        fixed.state.alpha.destinationFactor == "one-minus-src-alpha" &&
        fixed.state.alpha.operation == "add" && fixed.state.writeMask == "rgba"
}

private fun GPUCorePrimitiveClipStencilProducerGeometryAuthority.hasValidContours(): Boolean {
    return hasTriangleContours(vertices, contourStarts)
}

private fun GPUClipExecutionGeometry.Path.hasTriangleContours(): Boolean =
    hasTriangleContours(vertices, contourStarts)

private fun hasTriangleContours(vertices: List<Float>, contourStarts: List<Int>): Boolean {
    if (vertices.size < 6 || vertices.size % 2 != 0 || !vertices.all(Float::isFinite)) return false
    val vertexCount = vertices.size / 2
    if (contourStarts.isEmpty() || contourStarts.first() != 0 ||
        !contourStarts.zipWithNext().all { (left, right) -> left < right } ||
        contourStarts.last() !in 0 until vertexCount
    ) return false
    return contourStarts.indices.all { index ->
        val start = contourStarts[index]
        val end = contourStarts.getOrElse(index + 1) { vertexCount }
        end - start >= 3
    }
}

internal fun corePrimitiveClipStencilNdcVertices(
    vertices: List<Float>,
    width: Int,
    height: Int,
): List<Float>? {
    if (vertices.size < 4 || vertices.size % 2 != 0 ||
        !vertices.all(Float::isFinite) || width <= 0 || height <= 0
    ) return null
    val sealed = ArrayList<Float>(vertices.size)
    vertices.chunked(2).forEach { (x, y) ->
        val ndcX = x / width.toFloat() * 2f - 1f
        val ndcY = 1f - y / height.toFloat() * 2f
        if (!ndcX.isFinite() || !ndcY.isFinite()) return null
        sealed += ndcX
        sealed += ndcY
    }
    return immutableList(sealed)
}

private fun refused(code: String, message: String) =
    GPUCorePrimitiveClipStencilNativeRoute.Refused(code, message)
