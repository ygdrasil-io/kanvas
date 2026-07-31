package org.graphiks.kanvas.gpu.renderer.passes

import java.nio.ByteBuffer
import java.nio.ByteOrder
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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
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
    val semanticAuthority: GPUCorePrimitivePreparedSemanticAuthority,
    val coverageMode: GPUCorePrimitiveCoverageMode,
    val blendPlan: GPUBlendPlan,
    val orderingToken: GPUClipOrderingToken,
    val packetRole: GPUDrawPacketRole,
    val geometry: GPUCorePrimitiveGeometry,
) {
    init {
        require(commandId >= 0 && sourceOrder >= 0) {
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
    val semanticAuthority: GPUCorePrimitivePreparedSemanticAuthority,
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

/**
 * Closed result of authenticating live packet facts against the single builder-owned passive
 * coverage-mask route. Execution consumes only the returned passive facts.
 */
internal sealed interface GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation {
    data class Accepted(
        val route: GPUCorePrimitiveCoverageMaskPreparedRoute.Accepted,
        val resolvedMaskBytes: Long,
    ) : GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation

    data class Refused(
        val message: String,
    ) : GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation
}

internal fun validateGPUCorePrimitiveCoverageMaskPreparedAuthority(
    packets: List<GPUDrawPacket>,
    slabSeal: GPUCorePrimitiveCoverageMaskUniformSlabSeal,
): GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation {
    fun refused(message: String) =
        GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation.Refused(message)

    val producerCount = slabSeal.producerSlots.size
    val consumerCount = slabSeal.consumerSlots.size
    val sealedRoute = slabSeal.preparedRoute
    val plan = packets.firstOrNull()?.clipExecutionPlan as?
        GPUClipExecutionPlan.CoverageMask
        ?: return refused("Prepared coverage-mask producer is missing its typed execution plan.")
    val planIdentity = plan.canonicalIdentity()
    if (packets.size != producerCount + consumerCount || consumerCount < 2 ||
        producerCount != plan.producers.size || producerCount != sealedRoute.producers.size ||
        consumerCount != sealedRoute.consumers.size ||
        slabSeal.plan.slots.size != packets.size ||
        slabSeal.planCanonicalIdentity != planIdentity ||
        slabSeal.contentKey != plan.contentKey || slabSeal.maskBounds != plan.bounds ||
        slabSeal.orderingToken != plan.orderingToken.value ||
        sealedRoute.planCanonicalIdentity != planIdentity ||
        sealedRoute.contentKey != plan.contentKey || sealedRoute.bounds != plan.bounds ||
        sealedRoute.orderingToken != plan.orderingToken ||
        !slabSeal.hasCanonicalRenderPipelineKeys
    ) return refused("Prepared coverage-mask live plan authority was substituted.")

    var index = 0
    while (index < packets.size) {
        val packetPlan = packets[index].clipExecutionPlan as?
            GPUClipExecutionPlan.CoverageMask
            ?: return refused("Prepared coverage-mask packet lost its typed live plan.")
        if (packetPlan != plan) {
            return refused("Prepared coverage-mask packet live plans differ.")
        }
        index += 1
    }

    index = 0
    while (index < producerCount) {
        val packet = packets[index]
        val producer = plan.producers[index]
        val slot = slabSeal.producerSlots[index]
        val routeProducer = sealedRoute.producers[index]
        val packetProducer = packet.clipProducerAuthority as? GPUClipProducerAuthority.Mask
            ?: return refused("Prepared coverage-mask producer packet authority is missing.")
        val prepared = packet.corePrimitivePreparedAuthority
        if (packet.role != GPUDrawPacketRole.ClipProducer ||
            packetProducer.producer != producer || packetProducer.producer.sourceOrder != slot.sourceOrder ||
            producer.sourceOrder != routeProducer.sourceOrder ||
            producer.geometry != routeProducer.geometry || producer.combine != routeProducer.combine ||
            producer.antiAlias != routeProducer.antiAlias ||
            routeProducer.structuralKey != slot.structuralPipelineKey ||
            slot.packetId != packet.packetId || slot.commandId != packet.commandIdValue ||
            packet.uniformSlot != null || packet.resourceSlot != null ||
            packet.coverageMaskProducerUniformSlabSeal !== slabSeal.producerUniformSlabSeal ||
            prepared?.coverageMaskUniformSlabSeal !== slabSeal ||
            prepared.uniformSlabSeal != null || prepared.analyticShapeUniformSeal != null ||
            prepared.analyticClipUniformSeal != null ||
            prepared.analyticIntersectionUniformSeal != null ||
            prepared.structuralPipelineKey != slot.structuralPipelineKey ||
            prepared.renderPipelineKey != slot.renderPipelineKey ||
            packet.renderPipelineKey != slot.renderPipelineKey ||
            packet.bindingLayoutHash != CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_LAYOUT_KEY ||
            slot.bindingLayoutHash != CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_LAYOUT_KEY ||
            !slabSeal.hasExactCoverageMaskProducerPayload(index, plan, producer)
        ) return refused("Prepared coverage-mask producer route or ABI64 authority was substituted.")
        index += 1
    }

    index = 0
    while (index < consumerCount) {
        val packetIndex = producerCount + index
        val packet = packets[packetIndex]
        val slot = slabSeal.consumerSlots[index]
        val routeConsumer = sealedRoute.consumers[index]
        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
            ?: return refused("Prepared coverage-mask consumer semantic authority is missing.")
        val blend = packet.blendPlan
            ?: return refused("Prepared coverage-mask consumer blend authority is missing.")
        val prepared = packet.corePrimitivePreparedAuthority
        if (packet.role != GPUDrawPacketRole.Shading ||
            packet.packetId != slot.packetId || packet.commandIdValue != slot.commandId ||
            packet.originalPaintOrder != slot.sourceOrder ||
            packet.uniformSlot != semantic.payloadRef.uniformSlot ||
            packet.resourceSlot != slot.resourceSlot ||
            packet.renderStepId != slot.renderStepId ||
            packet.renderStepVersion != slot.renderStepVersion ||
            packet.resourceGeneration != slot.resourceGeneration ||
            packet.clipCoveragePlan != slot.clipCoveragePlan ||
            packet.frameProvenance != slot.frameProvenance ||
            packet.targetStateHash != slot.targetStateHash ||
            packet.vertexSourceLabel != slot.vertexSourceLabel ||
            packet.scissorBoundsHash != slot.scissorBoundsHash ||
            !slot.semanticAuthority.matches(semantic) ||
            prepared?.coverageMaskUniformSlabSeal !== slabSeal ||
            prepared.uniformSlabSeal != null || prepared.analyticShapeUniformSeal != null ||
            prepared.analyticClipUniformSeal != null ||
            prepared.analyticIntersectionUniformSeal != null ||
            prepared.structuralPipelineKey != slot.structuralPipelineKey ||
            prepared.renderPipelineKey != slot.renderPipelineKey ||
            packet.renderPipelineKey != slot.renderPipelineKey ||
            packet.bindingLayoutHash != CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_LAYOUT_KEY ||
            slot.bindingLayoutHash != CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_LAYOUT_KEY ||
            routeConsumer.packetId != packet.packetId || routeConsumer.commandId != packet.commandIdValue ||
            routeConsumer.sourceOrder != packet.originalPaintOrder ||
            routeConsumer.semanticAuthority !== slot.semanticAuthority ||
            routeConsumer.packetRole != packet.role ||
            routeConsumer.coverageMode != semantic.coverageMode ||
            routeConsumer.orderingToken != plan.orderingToken ||
            routeConsumer.structuralKey != slot.structuralPipelineKey ||
            !blend.isCanonicalPremulSrcOver() ||
            semantic.blendPlanIdentity != routeConsumer.blendCanonicalIdentity ||
            !routeConsumer.geometry.matchesLiveCoverageMaskGeometry(semantic.geometry) ||
            !slabSeal.hasExactCoverageMaskConsumerPayload(packetIndex, plan, semantic)
        ) return refused("Prepared coverage-mask consumer route or ABI64 authority was substituted.")
        index += 1
    }

    return GPUCorePrimitiveCoverageMaskPreparedAuthorityValidation.Accepted(
        sealedRoute,
        Math.multiplyExact(
            Math.multiplyExact(sealedRoute.bounds.width.toLong(), sealedRoute.bounds.height.toLong()),
            4L,
        ),
    )
}

private fun GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.matchesLiveCoverageMaskGeometry(
    live: GPUCorePrimitiveGeometry,
): Boolean = when (this) {
    is GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.Rect -> {
        val rect = live as? GPUCorePrimitiveGeometry.Rect ?: return false
        left.toRawBits() == rect.left.toRawBits() && top.toRawBits() == rect.top.toRawBits() &&
            right.toRawBits() == rect.right.toRawBits() &&
            bottom.toRawBits() == rect.bottom.toRawBits()
    }
    is GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.DirectTriangles -> {
        val path = live as? GPUCorePrimitiveGeometry.TriangulatedPath ?: return false
        path.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles && !path.inverseFill &&
            path.strokeStyle == null && vertices == path.vertices && indices == path.indices &&
            sourceContourStarts == path.sourceContourStarts &&
            sourceVertexCount == path.sourceVertexCount && coverBounds == path.coverBounds &&
            fillRule == path.fillRule
    }
}

private fun GPUCorePrimitiveCoverageMaskUniformSlabSeal.hasExactCoverageMaskProducerPayload(
    slotIndex: Int,
    clipPlan: GPUClipExecutionPlan.CoverageMask,
    producer: org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan,
): Boolean {
    val slot = plan.slots.getOrNull(slotIndex) ?: return false
    if (slot.payloadBytes != 64L || slot.alignedOffset < 0L ||
        slot.alignedOffset > packedBytesForUpload().size.toLong() - 64L
    ) return false
    val bytes = packedBytesForUpload()
    val base = slot.alignedOffset.toInt()
    val bounds = when (val geometry = producer.geometry) {
        is GPUClipExecutionGeometry.Rect -> geometry.bounds
        is GPUClipExecutionGeometry.RRect -> geometry.bounds
        is GPUClipExecutionGeometry.Path -> return false
    }
    if (!bytes.hasLittleEndianFloat(base, clipPlan.bounds.left.toFloat()) ||
        !bytes.hasLittleEndianFloat(base + 4, clipPlan.bounds.top.toFloat()) ||
        !bytes.hasLittleEndianFloat(base + 8, clipPlan.bounds.width.toFloat()) ||
        !bytes.hasLittleEndianFloat(base + 12, clipPlan.bounds.height.toFloat()) ||
        !bytes.hasLittleEndianFloat(base + 16, bounds.left) ||
        !bytes.hasLittleEndianFloat(base + 20, bounds.top) ||
        !bytes.hasLittleEndianFloat(base + 24, bounds.right) ||
        !bytes.hasLittleEndianFloat(base + 28, bounds.bottom)
    ) return false
    val radii = (producer.geometry as? GPUClipExecutionGeometry.RRect)?.radii
    var radiusIndex = 0
    while (radiusIndex < 8) {
        val expected = radii?.get(radiusIndex) ?: 0f
        if (!bytes.hasLittleEndianFloat(base + 32 + radiusIndex * 4, expected)) return false
        radiusIndex += 1
    }
    return true
}

private fun GPUCorePrimitiveCoverageMaskUniformSlabSeal.hasExactCoverageMaskConsumerPayload(
    slotIndex: Int,
    clipPlan: GPUClipExecutionPlan.CoverageMask,
    semantic: GPUDrawSemanticPayload.CorePrimitive,
): Boolean {
    val slot = plan.slots.getOrNull(slotIndex) ?: return false
    if (slot.payloadBytes != 64L || slot.alignedOffset < 0L ||
        slot.alignedOffset > packedBytesForUpload().size.toLong() - 64L
    ) return false
    val bytes = packedBytesForUpload()
    val base = slot.alignedOffset.toInt()
    if (!bytes.hasLittleEndianFloat(base, semantic.targetBounds.width.toFloat()) ||
        !bytes.hasLittleEndianFloat(base + 4, semantic.targetBounds.height.toFloat()) ||
        !bytes.hasLittleEndianInt(base + 8, clipPlan.bounds.left) ||
        !bytes.hasLittleEndianInt(base + 12, clipPlan.bounds.top) ||
        !bytes.hasLittleEndianInt(base + 16, clipPlan.bounds.width) ||
        !bytes.hasLittleEndianInt(base + 20, clipPlan.bounds.height)
    ) return false
    var zeroIndex = 24
    while (zeroIndex < 32) {
        if (bytes[base + zeroIndex].toInt() != 0) return false
        zeroIndex += 1
    }
    var colorIndex = 0
    while (colorIndex < 4) {
        if (!bytes.hasLittleEndianFloat(base + 32 + colorIndex * 4, semantic.premultipliedRgba[colorIndex])) {
            return false
        }
        colorIndex += 1
    }
    if (!bytes.hasLittleEndianInt(base + 48, if (clipPlan.consumer.invert) 1 else 0)) return false
    zeroIndex = 52
    while (zeroIndex < 64) {
        if (bytes[base + zeroIndex].toInt() != 0) return false
        zeroIndex += 1
    }
    return true
}

private fun ByteArray.hasLittleEndianFloat(offset: Int, expected: Float): Boolean =
    hasLittleEndianInt(offset, expected.toRawBits())

private fun ByteArray.hasLittleEndianInt(offset: Int, expected: Int): Boolean =
    offset >= 0 && offset <= size - Int.SIZE_BYTES &&
        this[offset].toInt() and 0xff == expected and 0xff &&
        this[offset + 1].toInt() and 0xff == expected ushr 8 and 0xff &&
        this[offset + 2].toInt() and 0xff == expected ushr 16 and 0xff &&
        this[offset + 3].toInt() and 0xff == expected ushr 24 and 0xff

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

/** Pure authentication of one exact ordered producer-only CoverageMask slab authority. */
internal fun validateCoverageMaskProducerUniformSlabSeal(
    packets: List<GPUDrawPacket>,
    seal: GPUCoverageMaskProducerUniformSlabSeal,
): Boolean {
    val plan = packets.firstOrNull()?.clipExecutionPlan as?
        GPUClipExecutionPlan.CoverageMask ?: return false
    if (packets.size != seal.producerSlots.size ||
        packets.map(GPUDrawPacket::packetId) != seal.producerPacketIds ||
        plan.producers.size != packets.size ||
        plan.contentKey != seal.contentKey ||
        plan.canonicalIdentity() != seal.planCanonicalIdentity ||
        plan.bounds != seal.maskBounds ||
        plan.orderingToken.value != seal.orderingToken ||
        !seal.hasCanonicalRenderPipelineKeys ||
        !seal.hasZeroPadding()
    ) return false
    packets.indices.forEach { index ->
        val packet = packets[index]
        val producer = plan.producers[index]
        val slot = seal.producerSlots[index]
        val authority = packet.clipProducerAuthority as?
            GPUClipProducerAuthority.Mask ?: return false
        val canonicalStructuralKey = try {
            corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(producer)
        } catch (_: IllegalStateException) {
            return false
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (packet.role != GPUDrawPacketRole.ClipProducer ||
            packet.clipExecutionPlan?.canonicalIdentity() != seal.planCanonicalIdentity ||
            authority.producer != producer ||
            slot.slotIndex != index || slot.sourceOrder != producer.sourceOrder ||
            slot.packetId != packet.packetId || slot.commandId != packet.commandIdValue ||
            slot.structuralPipelineKey != canonicalStructuralKey ||
            slot.renderPipelineKey != packet.renderPipelineKey ||
            slot.bindingLayoutHash != packet.bindingLayoutHash ||
            packet.coverageMaskProducerUniformSlabSeal !== seal ||
            !seal.hasExactPayload(
                index,
                corePrimitiveCoverageMaskProducerUniformBytes(plan, producer),
            )
        ) return false
    }
    return true
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
            structuralKey = corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(producer),
        )
    }
    val consumerSnapshots = consumers.zip(consumerGeometry).map { (consumer, geometry) ->
        GPUCorePrimitiveCoverageMaskPreparedConsumerSnapshot(
            packetId = consumer.packetId,
            commandId = consumer.commandId,
            sourceOrder = consumer.sourceOrder,
            semanticAuthority = consumer.semanticAuthority,
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

/** Canonical producer structural key shared by Core, TextA8, and ColorGlyph mask planning. */
internal fun corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
    producer: org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan,
): GPUCorePrimitiveRenderPipelineStructuralKey =
    corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(
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
                -> error("CoverageMask producer geometry must be validated before sealing")
            }
            is GPUClipExecutionGeometry.Path ->
                error("CoverageMask producer path geometry is outside the closed producer route")
        },
        combine = producer.combine,
    )

private fun requestRefused(code: String, message: String) =
    CoverageMaskRequestValidation.Refused(code, message)

private fun routeRefused(code: String, message: String) =
    GPUCorePrimitiveCoverageMaskPreparedRoute.Refused(code, message)
