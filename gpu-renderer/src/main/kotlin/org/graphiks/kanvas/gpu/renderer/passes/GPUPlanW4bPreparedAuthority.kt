package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.PlanResourceId
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectRawFacts
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectTransformRawFacts
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectTransformType
import org.graphiks.kanvas.gpu.renderer.resources.GPUCorePrimitiveFramePoolCapacities
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.corePrimitiveFramePoolCapacitiesOrNull
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectI32

/** Immutable W4b device RRect facts retained until the closed W4b lane materializes them. */
internal class W4bSessionScratchDrawV1(
    val packetId: GPUDrawPacketID,
    val commandId: Int,
    val origin: DrawOrigin,
    deviceShape: RRectF32,
    rasterBounds: RectI32,
    scissorBounds: GPUPixelBounds,
) {
    private val deviceShapeSnapshot = copyShape(deviceShape)
    private val rasterBoundsSnapshot = rasterBounds.copy()
    private val scissorBoundsSnapshot = scissorBounds.copy()

    fun copyDeviceShape(): RRectF32 = copyShape(deviceShapeSnapshot)
    fun copyRasterBounds(): RectI32 = rasterBoundsSnapshot.copy()
    fun copyScissorBounds(): GPUPixelBounds = scissorBoundsSnapshot.copy()

    internal fun sealedDeviceRawFacts(): GPUCorePrimitiveRRectRawFacts = rawFacts(deviceShapeSnapshot)

    internal fun isPositiveZeroRect(): Boolean = radii(deviceShapeSnapshot).all { radius ->
        radius.toRawBits() == 0f.toRawBits()
    }

    init {
        require(commandId >= 0) { "W4b scratch draw command id must be non-negative" }
        require(origin == DrawOrigin.RECT || origin == DrawOrigin.RRECT) {
            "W4b scratch draw requires RECT or RRECT provenance"
        }
        require(sealedDeviceRawFacts().hasValidGeometry()) {
            "W4b scratch draw requires a finite normalized device RRect"
        }
        require(radii(deviceShapeSnapshot).all { radius -> radius != 0f || radius.toRawBits() == 0f.toRawBits() }) {
            "W4b scratch draw requires canonical positive-zero radii"
        }
        require(origin != DrawOrigin.RECT || isPositiveZeroRect()) {
            "W4b RECT provenance requires the canonical zero-radius RRect"
        }
        require(!rasterBoundsSnapshot.isEmpty64() && !scissorBoundsSnapshot.isEmpty) {
            "W4b scratch draw requires non-empty raster and scissor bounds"
        }
    }

    private companion object {
        fun copyShape(shape: RRectF32): RRectF32 = RRectF32.of(
            shape.rect.copy(),
            shape.topLeft,
            shape.topRight,
            shape.bottomRight,
            shape.bottomLeft,
        )

        fun radii(shape: RRectF32): List<Float> = listOf(
            shape.topLeft.x,
            shape.topLeft.y,
            shape.topRight.x,
            shape.topRight.y,
            shape.bottomRight.x,
            shape.bottomRight.y,
            shape.bottomLeft.x,
            shape.bottomLeft.y,
        )

        fun rawFacts(shape: RRectF32): GPUCorePrimitiveRRectRawFacts = GPUCorePrimitiveRRectRawFacts(
            leftBits = shape.rect.left.toRawBits(),
            topBits = shape.rect.top.toRawBits(),
            rightBits = shape.rect.right.toRawBits(),
            bottomBits = shape.rect.bottom.toRawBits(),
            topLeftXBits = shape.topLeft.x.toRawBits(),
            topLeftYBits = shape.topLeft.y.toRawBits(),
            topRightXBits = shape.topRight.x.toRawBits(),
            topRightYBits = shape.topRight.y.toRawBits(),
            bottomRightXBits = shape.bottomRight.x.toRawBits(),
            bottomRightYBits = shape.bottomRight.y.toRawBits(),
            bottomLeftXBits = shape.bottomLeft.x.toRawBits(),
            bottomLeftYBits = shape.bottomLeft.y.toRawBits(),
        )
    }
}

/**
 * Planned-only authority for one immutable W4b device RRect.
 *
 * It deliberately signs the twelve already sealed device bits directly: source, normalized, and
 * device all name that one immutable snapshot and the transform is canonical identity.
 */
internal class GPUCorePrimitiveW4bPlannedRRectAuthority(scratchDraw: W4bSessionScratchDrawV1) {
    private val deviceFacts = scratchDraw.sealedDeviceRawFacts()

    val geometryInput: GPUCorePrimitiveGeometryInput.RRect = deviceFacts.toGeometryInput()
    val authority: GPUCorePrimitiveRRectGeometryAuthority = requireNotNull(
        GPUCorePrimitiveRRectGeometryAuthority.issue(
            source = deviceFacts,
            normalized = deviceFacts,
            transform = GPUCorePrimitiveRRectTransformRawFacts(
                type = GPUCorePrimitiveRectTransformType.Identity,
                translateXBits = 0f.toRawBits(),
                translateYBits = 0f.toRawBits(),
                scaleXBits = 1f.toRawBits(),
                scaleYBits = 1f.toRawBits(),
                skewXBits = 0f.toRawBits(),
                skewYBits = 0f.toRawBits(),
            ),
            device = deviceFacts,
        ),
    )
}

/** Handle-free W4b authority for one already sealed ScalarAA analytic-RRect frame. */
internal class W4bSessionScratchV1(
    val planId: String,
    val capabilitySealHash: String,
    val deviceGeneration: Long,
    val target: GPUFrameTargetRef,
    val staging: GPUFrameBufferRef,
    val targetBounds: GPUPixelBounds,
    val vertexResourceId: PlanResourceId,
    val indexResourceId: PlanResourceId,
    val uniformResourceId: PlanResourceId,
    packetIds: List<GPUDrawPacketID>,
    commandIds: List<Int>,
    draws: List<W4bSessionScratchDrawV1>,
    val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    val uniformPlan: GPUUniformSlabPlan,
    val uniformStrideBytes: Long,
    val vertexUsefulBytes: Long,
    val indexUsefulBytes: Long,
    val uniformUsefulBytes: Long,
    val vertexCapacityBytes: Long,
    val indexCapacityBytes: Long,
    val uniformCapacityBytes: Long,
    val poolCapacities: GPUCorePrimitiveFramePoolCapacities,
    val maxBufferSize: Long,
    val maxDynamicUniformBuffersPerPipelineLayout: Long,
) {
    val packetIds: List<GPUDrawPacketID> = immutableList(packetIds)
    val commandIds: List<Int> = immutableList(commandIds)
    val draws: List<W4bSessionScratchDrawV1> = immutableList(draws)

    init {
        require(planId.isNotBlank() && capabilitySealHash.isNotBlank()) {
            "W4b scratch requires exact plan and capability seals"
        }
        require(deviceGeneration >= 0L && !targetBounds.isEmpty) {
            "W4b scratch requires a current non-empty target"
        }
        require(maxBufferSize > 0L && maxDynamicUniformBuffersPerPipelineLayout >= 1L) {
            "W4b scratch requires observed positive buffer and dynamic-uniform limits"
        }
        require(
            packetIds.size in 1..512 &&
                packetIds.distinct().size == packetIds.size &&
                commandIds.size == packetIds.size &&
                commandIds.distinct().size == commandIds.size &&
                commandIds.all { it >= 0 } &&
                draws.size == packetIds.size &&
                draws.map(W4bSessionScratchDrawV1::packetId) == packetIds &&
                draws.map(W4bSessionScratchDrawV1::commandId) == commandIds &&
                draws.any { draw -> draw.origin == DrawOrigin.RRECT },
        ) { "W4b scratch requires one ordered packet, command, and RRect snapshot per draw" }
        require(
            structuralPipelineKey.shader == GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape &&
                structuralPipelineKey.topology == GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList &&
                structuralPipelineKey.sampleCount == 1 &&
                structuralPipelineKey.uniformLayout ==
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
        ) { "W4b scratch accepts only the analytic single-sample Uniform80 pipeline" }
        require(
            vertexUsefulBytes == packetIds.size.toLong() * VERTEX_BYTES_PER_DRAW &&
                indexUsefulBytes == packetIds.size.toLong() * INDEX_BYTES_PER_DRAW &&
                uniformStrideBytes == uniformPlan.alignmentBytes &&
                uniformUsefulBytes == packetIds.size.toLong() * uniformStrideBytes &&
                uniformPlan.sourceLabel == SOURCE_LABEL &&
                uniformPlan.deviceGeneration == deviceGeneration &&
                uniformPlan.uploadBudgetBytes == uniformCapacityBytes &&
                uniformPlan.totalBytes == uniformUsefulBytes &&
                uniformPlan.slots.size == packetIds.size &&
                uniformPlan.slots.withIndex().all { (index, slot) ->
                    slot.slotLabel == "analytic-shape-draw-${commandIds[index]}" &&
                        slot.payloadBytes == UNIFORM_PAYLOAD_BYTES &&
                        slot.allocatedBytes == uniformStrideBytes &&
                        slot.alignedOffset == index.toLong() * uniformStrideBytes
                },
        ) { "W4b scratch Uniform80 slab must exactly match ordered draw facts" }
        require(
            vertexCapacityBytes >= vertexUsefulBytes &&
                indexCapacityBytes >= indexUsefulBytes &&
                uniformCapacityBytes >= uniformUsefulBytes &&
                vertexCapacityBytes <= maxBufferSize &&
                indexCapacityBytes <= maxBufferSize &&
                uniformCapacityBytes <= maxBufferSize &&
                poolCapacities.vertexBytes == vertexCapacityBytes &&
                poolCapacities.indexBytes == indexCapacityBytes &&
                poolCapacities.uniformBytes == uniformCapacityBytes &&
                poolCapacities == corePrimitiveFramePoolCapacitiesOrNull(
                vertexUsefulBytes,
                indexUsefulBytes,
                uniformUsefulBytes,
            ),
        ) { "W4b scratch reserved capacities must be the graph's exact native pool capacities" }
    }

    internal fun matches(
        expectedPlanId: String,
        capabilityHash: String,
        generation: Long,
        expectedTarget: GPUFrameTargetRef,
        expectedStaging: GPUFrameBufferRef,
        bounds: GPUPixelBounds,
        packets: List<GPUDrawPacket>,
    ): Boolean =
        planId == expectedPlanId &&
            capabilitySealHash == capabilityHash &&
            deviceGeneration == generation &&
            target == expectedTarget &&
            staging == expectedStaging &&
            targetBounds == bounds &&
            packetIds == packets.map(GPUDrawPacket::packetId) &&
            commandIds == packets.map(GPUDrawPacket::commandIdValue)

    internal companion object {
        const val SOURCE_LABEL: String = "core-primitive-analytic-shape-uniform-pass"
        const val UNIFORM_PAYLOAD_BYTES: Long = 80L
        private const val VERTEX_BYTES_PER_DRAW: Long = 32L
        private const val INDEX_BYTES_PER_DRAW: Long = 24L
    }
}
