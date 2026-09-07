package org.graphiks.kanvas.gpu.renderer.passes

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.plan.PathDrawGeometry
import org.graphiks.kanvas.gpu.plan.PathFillStrategy
import org.graphiks.kanvas.gpu.plan.PlanPassId
import org.graphiks.kanvas.gpu.plan.PlanResourceId
import org.graphiks.kanvas.gpu.plan.W4dPathStrokePlanCompiler
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUCorePrimitiveFramePoolCapacities
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.corePrimitiveFramePoolCapacitiesOrNull
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan
import org.graphiks.kanvas.gpu.renderer.state.GPUPathSourceAuthority
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillLimitsI32
import org.graphiks.math.geometry.PathStrokeDashF64
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.PathStrokeStyleF64

/** Immutable planned path-draw facts for one W4d visual draw. */
internal class W4dSessionScratchDrawV1(
    val commandId: Int,
    val strategy: PathFillStrategy,
    pathGeometry: PathDrawGeometry,
    val mode: PathStrokeDrawMode?,
    styleF64: PathStrokeStyleF64?,
    scissorBounds: GPUPixelBounds,
    val vertexOffsetBytes: Long,
    val vertexRangeBytes: Long,
    val indexOffsetBytes: Long,
    val indexRangeBytes: Long,
    val uniformSlotIndex: Int,
    val atomicGroupId: String?,
) {
    private val pathGeometrySnapshot: PathDrawGeometry = pathGeometry
    private val geometrySnapshotF32: PathFillGeometryF32 = when (pathGeometrySnapshot) {
        is PathDrawGeometry.Fill -> pathGeometrySnapshot.valueF32
        is PathDrawGeometry.Stroke -> pathGeometrySnapshot.valueF32.copyFillGeometryF32()
    }
    val styleF64: PathStrokeStyleF64? = styleF64?.snapshot()
    private val scissorBoundsSnapshot: GPUPixelBounds = scissorBounds.copy()
    val fillRule: FillRule = geometrySnapshotF32.fillRule
    val isStroke: Boolean = pathGeometrySnapshot is PathDrawGeometry.Stroke

    fun copyGeometryF32(): PathFillGeometryF32 = geometrySnapshotF32

    fun copyPathGeometry(): PathDrawGeometry = pathGeometrySnapshot

    fun copyStrokeGeometryF32OrNull(): PathStrokeGeometryF32? =
        (pathGeometrySnapshot as? PathDrawGeometry.Stroke)?.valueF32

    fun copyScissorBounds(): GPUPixelBounds = scissorBoundsSnapshot.copy()

    init {
        val isDirect = geometrySnapshotF32.copyDirectTriangleF32OrNull() != null
        val isStencil = geometrySnapshotF32.copyStencilEdgeFanF32OrNull() != null
        val expectedVertexBytes = Math.multiplyExact(geometrySnapshotF32.vertexCostI64, VERTEX_BYTES)
        val expectedIndexBytes = Math.multiplyExact(geometrySnapshotF32.indexCostI64, INDEX_BYTES)
        require(commandId >= 0 && !scissorBoundsSnapshot.isEmpty) {
            "W4d scratch draw requires a non-negative command and non-empty scissor"
        }
        require(vertexOffsetBytes >= 0L && indexOffsetBytes >= 0L && uniformSlotIndex >= 0) {
            "W4d scratch draw offsets and uniform slot must be non-negative"
        }
        require(vertexRangeBytes == expectedVertexBytes && indexRangeBytes == expectedIndexBytes) {
            "W4d scratch draw ranges must retain exact immutable geometry costs"
        }
        require(
            if (isStroke) mode != null && this.styleF64 != null else mode == null && this.styleF64 == null,
        ) { "W4d scratch draw stroke mode and style must match its sealed geometry kind" }
        require(
            when (strategy) {
                PathFillStrategy.DirectTriangle ->
                    isDirect && !isStencil && fillRule == FillRule.WINDING && atomicGroupId == null
                PathFillStrategy.StencilCover ->
                    !isDirect && isStencil && fillRule in setOf(FillRule.WINDING, FillRule.EVEN_ODD) &&
                        !atomicGroupId.isNullOrBlank()
            },
        ) { "W4d scratch draw strategy must retain its exact immutable geometry authority" }
        require(
            strategy != PathFillStrategy.StencilCover ||
                fillRule != FillRule.WINDING ||
                geometrySnapshotF32.emittedNonZeroClosedEdgeCountI32 <= UByte.MAX_VALUE.toInt(),
        ) { "W4d winding stencil authority must fit the sealed stencil8 range" }
        require(geometrySnapshotF32.emittedNonZeroClosedEdgeCountI32 <=
            PathFillLimitsI32().maxAttemptedEdgesPerPathI32
        ) { "W4d scratch draw exceeds the immutable math path edge limit" }
    }

    private companion object {
        const val VERTEX_BYTES: Long = 8L
        const val INDEX_BYTES: Long = 4L
    }
}

/**
 * Handle-free authority for one closed W4d path-draw frame.
 *
 * Geometry remains the immutable `:math` authority selected by the graph; this class only seals
 * its fixed native offsets, dynamic-uniform slots, resource identities, and capability evidence.
 */
internal class W4dSessionScratchV1(
    val planId: String,
    val capabilitySealHash: String,
    val deviceGeneration: Long,
    val target: GPUFrameTargetRef,
    val staging: GPUFrameBufferRef,
    val targetBounds: GPUPixelBounds,
    val vertexResourceId: PlanResourceId,
    val indexResourceId: PlanResourceId,
    val uniformResourceId: PlanResourceId,
    val depthStencilResourceId: PlanResourceId?,
    val targetBytes: Long,
    val stagingBytes: Long,
    val capabilityId: String,
    renderPassIds: List<PlanPassId>,
    val readbackPassId: PlanPassId,
    val resourceLastPassIndexExclusive: Int,
    val depthStencilFirstPassIndex: Int?,
    draws: List<W4dSessionScratchDrawV1>,
    val uniformPlan: GPUUniformSlabPlan,
    val uniformStrideBytes: Long,
    val vertexUsefulBytes: Long,
    val indexUsefulBytes: Long,
    val uniformUsefulBytes: Long,
    val vertexCapacityBytes: Long,
    val indexCapacityBytes: Long,
    val uniformCapacityBytes: Long,
    val depthStencilBytes: Long,
    val poolCapacities: GPUCorePrimitiveFramePoolCapacities,
    val maxBufferSize: Long,
    val maxDynamicUniformBuffersPerPipelineLayout: Long,
) {
    val graphHash: String = planId
    val capabilityHash: String = capabilitySealHash
    val draws: List<W4dSessionScratchDrawV1> = immutableList(draws)
    val renderPassIds: List<PlanPassId> = immutableList(renderPassIds)

    init {
        val usesStencil = this.draws.any { draw -> draw.strategy == PathFillStrategy.StencilCover }
        val expectedVertexUseful = this.draws.checkedSumOf(W4dSessionScratchDrawV1::vertexRangeBytes)
        val expectedIndexUseful = this.draws.checkedSumOf(W4dSessionScratchDrawV1::indexRangeBytes)
        val expectedUniformPayload = Math.multiplyExact(this.draws.size.toLong(), UNIFORM_PAYLOAD_BYTES)
        val expectedUniformReserved = Math.multiplyExact(this.draws.size.toLong(), uniformStrideBytes)
        require(planId.isCanonicalSha256() && capabilitySealHash.isNotBlank() && deviceGeneration >= 0L) {
            "W4d scratch requires exact graph and capability hashes"
        }
        require(capabilityId == W4dPathStrokePlanCompiler.CAPABILITY_ID) {
            "W4d scratch requires the exact W4d capability id"
        }
        require(
            this.renderPassIds.isNotEmpty() && this.renderPassIds.distinct().size == this.renderPassIds.size &&
                readbackPassId !in this.renderPassIds && resourceLastPassIndexExclusive == this.renderPassIds.size + 1,
        ) { "W4d scratch requires exact ordered graph pass identities and lifetimes" }
        require(!targetBounds.isEmpty && maxBufferSize > 0L && maxDynamicUniformBuffersPerPipelineLayout >= 1L) {
            "W4d scratch requires current target and observed buffer facts"
        }
        require(targetBytes > 0L && stagingBytes > 0L) {
            "W4d scratch requires exact positive target and staging sizes"
        }
        require(
            this.draws.size in 1..512 &&
                this.draws.map(W4dSessionScratchDrawV1::commandId).distinct().size == this.draws.size &&
                this.draws.zipWithNext().all { (first, second) -> first.commandId < second.commandId } &&
                this.draws.withIndex().all { (index, draw) -> draw.uniformSlotIndex == index } &&
                this.draws.zipWithNext().all { (first, second) ->
                    Math.addExact(first.vertexOffsetBytes, first.vertexRangeBytes) == second.vertexOffsetBytes &&
                        Math.addExact(first.indexOffsetBytes, first.indexRangeBytes) == second.indexOffsetBytes
                } &&
                this.draws.first().vertexOffsetBytes == 0L && this.draws.first().indexOffsetBytes == 0L,
        ) { "W4d scratch requires ordered exact draw offsets" }
        require(
            vertexUsefulBytes == expectedVertexUseful &&
                indexUsefulBytes == expectedIndexUseful &&
                uniformUsefulBytes == expectedUniformPayload &&
                uniformStrideBytes == uniformPlan.alignmentBytes &&
                uniformPlan.sourceLabel == SOURCE_LABEL &&
                uniformPlan.deviceGeneration == deviceGeneration &&
                uniformPlan.uploadBudgetBytes == uniformCapacityBytes &&
                uniformPlan.totalBytes == expectedUniformReserved &&
                uniformPlan.slots.size == this.draws.size &&
                uniformPlan.slots.withIndex().all { (index, slot) ->
                    slot.slotLabel == "path-draw-${this.draws[index].commandId}" &&
                        slot.payloadBytes == UNIFORM_PAYLOAD_BYTES &&
                        slot.allocatedBytes == uniformStrideBytes &&
                        slot.alignedOffset == index.toLong() * uniformStrideBytes
                },
        ) { "W4d scratch Uniform32 slab must exactly match the graph draw order" }
        require(
            vertexCapacityBytes >= vertexUsefulBytes &&
                indexCapacityBytes >= indexUsefulBytes &&
                uniformCapacityBytes >= uniformPlan.totalBytes &&
                vertexCapacityBytes <= maxBufferSize &&
                indexCapacityBytes <= maxBufferSize &&
                uniformCapacityBytes <= maxBufferSize &&
                poolCapacities.vertexBytes == vertexCapacityBytes &&
                poolCapacities.indexBytes == indexCapacityBytes &&
                poolCapacities.uniformBytes == uniformCapacityBytes &&
                poolCapacities == corePrimitiveFramePoolCapacitiesOrNull(
                vertexUsefulBytes,
                indexUsefulBytes,
                uniformPlan.totalBytes,
            ),
        ) { "W4d scratch capacities must be the graph's exact pooled reservations" }
        require(
            if (usesStencil) {
                depthStencilResourceId != null && depthStencilBytes > 0L &&
                    depthStencilFirstPassIndex != null && depthStencilFirstPassIndex in this.renderPassIds.indices
            } else {
                depthStencilResourceId == null && depthStencilBytes == 0L && depthStencilFirstPassIndex == null
            },
        ) { "W4d scratch depth-stencil authority must match the sealed strategies" }
    }

    internal fun matches(
        expectedPlanId: String,
        expectedCapabilityHash: String,
        expectedGeneration: Long,
        expectedTarget: GPUFrameTargetRef,
        expectedStaging: GPUFrameBufferRef,
        expectedBounds: GPUPixelBounds,
    ): Boolean =
        planId == expectedPlanId &&
            capabilitySealHash == expectedCapabilityHash &&
            deviceGeneration == expectedGeneration &&
            target == expectedTarget &&
            staging == expectedStaging &&
            targetBounds == expectedBounds

    internal fun matchesPreparedPacket(
        expectedPlanId: String,
        expectedCapabilityHash: String,
        packet: GPUDrawPacket,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        renderPipelineKey: GPURenderPipelineKey,
    ): Boolean {
        if (
            planId != expectedPlanId ||
                capabilitySealHash != expectedCapabilityHash ||
                expectedPlanId.isBlank() ||
                expectedCapabilityHash.isBlank() ||
                packet.renderPipelineKey != renderPipelineKey ||
                packet.originalPaintOrder < 0 ||
                packet.sortKey != packet.originalPaintOrder.toLong() ||
                packet.sortKeyPreimage != "paint-order:${packet.originalPaintOrder}" ||
                structuralPipelineKey.sampleCount != 1 ||
                structuralPipelineKey.uniformLayout !=
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.DynamicUniform32V2
        ) return false
        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
        val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath ?: return false
        val draw = draws.singleOrNull { scratchDraw -> scratchDraw.commandId == packet.commandIdValue } ?: return false
        val uniformSlot = uniformPlan.slots.getOrNull(draw.uniformSlotIndex) ?: return false
        val semanticUniform = semantic.payloadRef.uniformSlot ?: return false
        val uniformBytes = semantic.payloadRef.uniformBlock?.bytes ?: return false
        if (
            packet.uniformSlot != semanticUniform ||
                semantic.payloadRef.commandIdValue != packet.commandIdValue ||
                semanticUniform.slotId.value != "core-primitive:${packet.commandIdValue}" ||
                semanticUniform.byteOffset != 0L ||
                uniformBytes.size.toLong() != UNIFORM_PAYLOAD_BYTES ||
                uniformSlot.slotLabel != "path-draw-${draw.commandId}" ||
                uniformSlot.payloadBytes != UNIFORM_PAYLOAD_BYTES ||
                uniformSlot.allocatedBytes != uniformStrideBytes ||
                uniformSlot.alignedOffset != draw.uniformSlotIndex.toLong() * uniformStrideBytes ||
                uniformSlot.payloadHash != sha256Hex(uniformBytes) ||
                packet.originalPaintOrder != draw.uniformSlotIndex ||
                packet.analysisRecordId != "analysis.w4d_path_draw.${draw.commandId}" ||
                semantic.sourceFamily != GPUCorePrimitiveSourceFamily.Path ||
                semantic.targetBounds != targetBounds ||
                semantic.scissorBounds != draw.copyScissorBounds() ||
                geometry.coverBounds != draw.copyScissorBounds() ||
                geometry.sourceAuthority != GPUPathSourceAuthority.W4dPlannedPathStrokeV1 ||
                geometry.inverseFill ||
                geometry.strokeStyle != null ||
                !semantic.hasCanonicalHashIntegrity() ||
                !hasExactPacketRanges(draw)
        ) return false
        val matchesGeometry = when (draw.strategy) {
            PathFillStrategy.DirectTriangle -> matchesDirectPacket(
                draw = draw,
                packet = packet,
                structuralPipelineKey = structuralPipelineKey,
                semantic = semantic,
                geometry = geometry,
            )
            PathFillStrategy.StencilCover -> matchesStencilPacket(
                draw = draw,
                packet = packet,
                structuralPipelineKey = structuralPipelineKey,
                semantic = semantic,
                geometry = geometry,
            )
        }
        return matchesGeometry && matchesExactW4dStructuralPipeline(
            draw = draw,
            packet = packet,
            semantic = semantic,
            structuralPipelineKey = structuralPipelineKey,
            renderPipelineKey = renderPipelineKey,
        )
    }

    /**
     * Re-derives W4d's executable key from sealed math and packet facts before sealing an
     * authority. This binds the producer fill rule, regular cover state, direct family,
     * single-sample target, canonical SrcOver blend, and scissor/no-clip facts together.
     */
    private fun matchesExactW4dStructuralPipeline(
        draw: W4dSessionScratchDrawV1,
        packet: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        renderPipelineKey: GPURenderPipelineKey,
    ): Boolean {
        val clipExecutionPlan = packet.clipExecutionPlan ?: return false
        val canonicalBlend = canonicalSolidRectSrcOverBlendPlan()
        if (
            !clipExecutionPlan.isCorePrimitiveNoClipOrScissorExecution() ||
                packet.blendPlan?.canonicalIdentity() != canonicalBlend.canonicalIdentity() ||
                semantic.blendPlanIdentity != canonicalBlend.canonicalIdentity() ||
                semantic.clipExecutionPlanIdentity != clipExecutionPlan.canonicalIdentity()
        ) return false
        val expected = try {
            when (draw.strategy) {
                PathFillStrategy.DirectTriangle -> {
                    if (packet.role != GPUDrawPacketRole.Shading) return false
                    corePrimitiveRenderPipelineStructuralKey(
                        semantic = semantic,
                        clipExecutionPlan = clipExecutionPlan,
                        blendPlan = canonicalBlend,
                        sampleCount = 1,
                        colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
                    )
                }
                PathFillStrategy.StencilCover -> {
                    val role = when (packet.role) {
                        GPUDrawPacketRole.PathStencilProducer ->
                            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer
                        GPUDrawPacketRole.PathStencilCover ->
                            GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover
                        else -> return false
                    }
                    corePrimitivePathStencilRenderPipelineStructuralKey(
                        semantic = semantic,
                        role = role,
                        clipExecutionPlan = clipExecutionPlan,
                        blendPlan = canonicalBlend,
                        sampleCount = 1,
                        colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
                    )
                }
            }
        } catch (_: IllegalArgumentException) {
            return false
        }
        return structuralPipelineKey == expected &&
            renderPipelineKey == expected.stableRenderPipelineKey(
                CORE_PRIMITIVE_STRUCTURAL_PIPELINE_BASE_KEY,
            )
    }

    private fun hasExactPacketRanges(draw: W4dSessionScratchDrawV1): Boolean = try {
        Math.addExact(draw.vertexOffsetBytes, draw.vertexRangeBytes) <= vertexUsefulBytes &&
            Math.addExact(draw.indexOffsetBytes, draw.indexRangeBytes) <= indexUsefulBytes &&
            draw.vertexRangeBytes == Math.multiplyExact(draw.copyGeometryF32().vertexCostI64, VERTEX_BYTES) &&
            draw.indexRangeBytes == Math.multiplyExact(draw.copyGeometryF32().indexCostI64, INDEX_BYTES)
    } catch (_: ArithmeticException) {
        false
    }

    private fun matchesDirectPacket(
        draw: W4dSessionScratchDrawV1,
        packet: GPUDrawPacket,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        geometry: GPUCorePrimitiveGeometry.TriangulatedPath,
    ): Boolean {
        val direct = draw.copyGeometryF32().copyDirectTriangleF32OrNull() ?: return false
        return packet.role == GPUDrawPacketRole.Shading &&
            structuralPipelineKey.role == GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading &&
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor &&
            geometry.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles &&
            geometry.fillRule == GPUCorePrimitiveFillRule.Winding &&
            geometry.sourceContourStarts == listOf(0) &&
            geometry.sourceVertexCount == direct.vertexCountI32 &&
            geometry.vertices.hasSameRawBits(direct.copyVerticesF32().toList()) &&
            geometry.indices == direct.copyIndicesI32().toList()
    }

    private fun matchesStencilPacket(
        draw: W4dSessionScratchDrawV1,
        packet: GPUDrawPacket,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        geometry: GPUCorePrimitiveGeometry.TriangulatedPath,
    ): Boolean {
        val fan = draw.copyGeometryF32().copyStencilEdgeFanF32OrNull() ?: return false
        val expectedFillRule = when (draw.fillRule) {
            FillRule.WINDING -> GPUCorePrimitiveFillRule.Winding
            FillRule.EVEN_ODD -> GPUCorePrimitiveFillRule.EvenOdd
            else -> return false
        }
        val expectedRole = when (packet.role) {
            GPUDrawPacketRole.PathStencilProducer -> GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer
            GPUDrawPacketRole.PathStencilCover -> GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover
            else -> return false
        }
        return structuralPipelineKey.role == expectedRole &&
            semantic.coverageMode == GPUCorePrimitiveCoverageMode.Stencil1x &&
            geometry.geometryMode == GPUCorePrimitiveGeometryMode.StencilEdgeFan &&
            geometry.fillRule == expectedFillRule &&
            geometry.sourceContourStarts == fan.copyContourStartsI32().toList() &&
            geometry.sourceVertexCount == fan.edgeCountI32 &&
            geometry.vertices.hasSameRawBits(fan.copyVerticesF32().toList()) &&
            geometry.indices == fan.copyIndicesI32().toList()
    }

    internal companion object {
        const val SOURCE_LABEL: String = "w4d-path-draw-uniform32-pass"
        const val UNIFORM_PAYLOAD_BYTES: Long = 32L
        private const val VERTEX_BYTES: Long = 8L
        private const val INDEX_BYTES: Long = 4L
    }
}

private fun PathStrokeStyleF64.snapshot(): PathStrokeStyleF64 = PathStrokeStyleF64(
    widthF64 = widthF64,
    cap = cap,
    join = join,
    miterLimitF64 = miterLimitF64,
    dashF64 = dashF64?.let { dash -> PathStrokeDashF64.of(dash.copyIntervalsF64(), dash.phaseF64) },
)

private inline fun <T> Iterable<T>.checkedSumOf(selector: (T) -> Long): Long =
    fold(0L) { total, value -> Math.addExact(total, selector(value)) }

private fun String.isCanonicalSha256(): Boolean =
    length == 64 && all { character -> character in '0'..'9' || character in 'a'..'f' }

private fun List<Float>.hasSameRawBits(other: List<Float>): Boolean =
    size == other.size && indices.all { index -> this[index].toRawBits() == other[index].toRawBits() }

private fun sha256Hex(bytes: List<Int>): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes.map(Int::toByte).toByteArray())
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
