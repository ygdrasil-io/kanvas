package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanCapabilityAdapterResult
import org.graphiks.kanvas.gpu.renderer.planning.toPlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesStagingLayout
import org.graphiks.kanvas.gpu.renderer.destination.preparedTextDestinationBounds
import org.graphiks.kanvas.gpu.renderer.destination.preparedDestinationIntersection
import org.graphiks.kanvas.gpu.renderer.execution.preparedVerticesIndexBufferBytesI64
import org.graphiks.kanvas.gpu.renderer.execution.preparedVerticesDrawUniformBytesI64
import org.graphiks.kanvas.gpu.renderer.recording.preparedTextDrawUniformStrideBytesI64
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.preparedCoreDestinationCapacityBoundsV6
import org.graphiks.kanvas.gpu.renderer.resources.preparedR8StagingRowBytesI64
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeI32

/** One closed prepared frame; every material is a ref into its one common publication. */
internal class W5hPreparedMaterialFrameV6 private constructor(
    val sourceFrame: PreparedSourceFrameV6?,
    val text: GPUPreparedTextFrameInventoryPreparation.Ready,
    val vertices: PreparedVerticesFrameInventory,
    val corePlans: Map<Int, EffectiveMaterialPlanner.Result.Ready>,
    val elidedOperationIndices: Set<Int>,
) {
    companion object {
        fun prepare(request: GPUPreparedSurfaceFrameBuildRequest, generationI32: Int): W5hPreparedMaterialFrameV6? {
            val operations = request.candidate.operations
            if (operations.none { it is DisplayOp.DrawText || it is DisplayOp.DrawVertices || it is DisplayOp.DrawMesh }) return null
            val indexed = operations.withIndex().filterNot { (_, operation) ->
                operation is DisplayOp.SetTransform || operation is DisplayOp.SetClip || operation is DisplayOp.Annotation
            }
            // Whole-frame closure precedes every source capture, catalogue, source index or owner.
            // Clear/DrawColor, fractional/AA Rect and other lanes retain their complete historical route.
            if (indexed.any { (_, op) -> when (op) {
                    is DisplayOp.DrawRect -> op.paint.isStroke()
                    is DisplayOp.DrawText, is DisplayOp.DrawVertices -> false
                    is DisplayOp.DrawMesh -> op.mesh.program != null
                    else -> true
                } }) return null
            val textGeometry = ArrayList<GPUPreparedTextGeometry>()
            val verticesGeometry = ArrayList<GPUPreparedVerticesGeometry>()
            val survivingRects = linkedMapOf<Int, org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds>()
            for ((index, operation) in indexed) when (operation) {
                is DisplayOp.DrawText -> {
                    val admitted = GPUPreparedTextLowerer.lower(operation, index, request.targetFacts,
                        request.capabilities, GPUPreparedFontTypefaceResolver, geometryOnly = true)
                        as? GPUPreparedTextLowering.GeometryReady ?: return null
                    textGeometry += admitted.geometry ?: return null
                }
                is DisplayOp.DrawVertices, is DisplayOp.DrawMesh -> {
                    val admitted = GPUPreparedVerticesLowerer.lower(operation, index, request.targetFacts,
                        request.capabilities, geometryOnly = true) as? GPUPreparedVerticesLowering.GeometryReady ?: return null
                    verticesGeometry += admitted.geometry
                }
                is DisplayOp.DrawRect -> {
                    val mapped = GPUOpMapper.mapOperations(listOf(operation), request.targetFacts,
                        request.candidate.config, request.capabilities, w5aPointMaterialRefs = mapOf(0 to MaterialPlanRef(0)))
                    if (mapped.preparedRefusal != null || mapped.visualCommands.any {
                            !it.isInPreparedPointDomain(request.targetBounds, false) }) return null
                    if (mapped.visualCommands.isNotEmpty()) survivingRects[index] =
                        mapped.visualCommands.single().preparedRectDestinationBounds(request.targetBounds)
                }
                else -> error("Closed prepared source operation changed")
            }
            // Real CPU rasterization, atlas placement, instance encoding and buffer ranges are
            // completed without a material program. These are the existing inventory owners.
            val textLimits = GPUPreparedTextFramePreparer.defaultLimits(request.targetFacts, request.capabilities)
            // CPU-only admission is bounded per real draw. Final survivors reapply the
            // unmodified historical limits below, before source publication or GPU ownership.
            fun expanded(valueI32: Int, hardCapI32: Int): Int = Math.multiplyExact(valueI32.toLong(),
                maxOf(1, textGeometry.size).toLong()).coerceAtMost(hardCapI32.toLong()).toInt()
            val admissionLimits = textLimits.copy(maxGlyphs = expanded(textLimits.maxGlyphs, 65_536),
                maxInstances = expanded(textLimits.maxInstances, 65_536),
                maxSubRuns = expanded(textLimits.maxSubRuns, 16_384),
                maxInstanceBytes = expanded(textLimits.maxInstanceBytes, 16 * 1_024 * 1_024))
            val admittedCoverage = when (val result = PreparedTextFrameInventoryBuilder.prepareGeometry(textGeometry,
                GPUTextArtifactGeneration(generationI32), admissionLimits)) {
                is PreparedTextCoverageInventory -> result
                is PreparedTextFrameInventoryResult.Refused -> throw IllegalArgumentException(result.code)
            }
            // Only actual rasterized sub-runs consume a source. Project empty Text together
            // with its occurrence before capture: it must never acquire a material owner.
            val consumingTextIndices = admittedCoverage.subRunInstances.mapTo(linkedSetOf()) { it.first }
            val zeroConsumerTextIndices = textGeometry.filter { it.operationIndex !in consumingTextIndices }
                .mapTo(linkedSetOf()) { it.operationIndex }
            val consumingOperations = indexed.filterNot { it.index in zeroConsumerTextIndices }
            val verticesLimits = GPUPreparedVerticesFramePreparer.defaultLimits(request.capabilities)
            val admittedGeometry = when (val result = PreparedVerticesFrameInventoryBuilder.prepareGeometry(verticesGeometry,
                verticesLimits, request.capabilities)) {
                is PreparedVerticesGeometryInventory -> result
                is PreparedVerticesFrameInventoryResult.Refused -> throw IllegalArgumentException(result.code)
            }
            // These are the mapper's actual clip/coverage predicates, applied to the real
            // CPU inventory before any source exists. Empty clips are topology, not NoOp blends.
            val culledGeometryIndices = textGeometry.filter {
                it.coveragePlan.toPreparedScissorBounds(request.targetBounds) == null
            }.mapTo(linkedSetOf()) { it.operationIndex }
            verticesGeometry.filter { it.culledByClip }.mapTo(culledGeometryIndices) { it.operationIndex }
            indexed.filter { it.value is DisplayOp.DrawRect && it.index !in survivingRects }
                .mapTo(culledGeometryIndices) { it.index }
            // An out-of-target nonempty sub-run is not a representable prepared semantic.
            // Preserve that whole frame's historical admission before catalogue/capture.
            if (admittedCoverage.subRunInstances.any { (index, instances) ->
                    index !in culledGeometryIndices && instances.preparedTextBounds(request.targetFacts) == null
                } || verticesGeometry.any { it.operationIndex !in culledGeometryIndices &&
                    (it.clippedBounds ?: it.deviceBounds).preparedVerticesPixelBounds(request.targetBounds).isEmpty }) return null
            val capabilities = when (val result = request.capabilities.toPlanCapabilitySnapshot(request.deviceGeneration)) {
                is GpuPlanCapabilityAdapterResult.Supported -> result.snapshot
                is GpuPlanCapabilityAdapterResult.Unsupported -> throw IllegalArgumentException(result.diagnostic.code.value)
            }
            val widthI32 = request.targetBounds.width
            val heightI32 = request.targetBounds.height
            val domain = RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat())
            val catalog = RuntimeEffectSemanticCatalog.builtinSnapshot()
            val allCaptures = consumingOperations.associate { (index, operation) ->
                val scene = DisplayOpSceneAdapter.capture(listOf(operation), SceneExtent(widthI32, heightI32), ColorSpace.SRGB)
                    as? SceneCaptureResult.Captured ?: error("Closed prepared source capture refused")
                val draw = requireNotNull(scene.scene.singleOrNull() as? SceneCommand.Draw)
                index to PreparedSourceCaptureV6.capture(draw.node, domain,
                    if (operation is DisplayOp.DrawText) CoveragePlan.AnalyticScalarAA else CoveragePlan.FullOrScissor,
                    BlendTargetClampV1.UnitInterval, catalog)
            }
            val elided = allCaptures.filter { (index, capture) -> capture.blend == BlendPlan.NoOpV1 || index in culledGeometryIndices }
            val elidedIndices = elided.keys + zeroConsumerTextIndices
            val captures = allCaptures.filterKeys { it !in elided }
            val survivingText = textGeometry.filterNot { it.operationIndex in elidedIndices }
            val survivingVertices = verticesGeometry.filterNot { it.operationIndex in elided }
            val coverage = when (val result = PreparedTextFrameInventoryBuilder.prepareGeometry(survivingText,
                GPUTextArtifactGeneration(generationI32), textLimits,
                textGeometry.map { it.operationIndex }.filterTo(linkedSetOf()) { it in elidedIndices })) {
                is PreparedTextCoverageInventory -> result
                is PreparedTextFrameInventoryResult.Refused -> throw IllegalArgumentException(result.code)
            }
            val geometry = when (val result = PreparedVerticesFrameInventoryBuilder.prepareGeometry(survivingVertices,
                verticesLimits, request.capabilities)) {
                is PreparedVerticesGeometryInventory -> result
                is PreparedVerticesFrameInventoryResult.Refused -> throw IllegalArgumentException(result.code)
            }
            val budget = PlanBudget(request.candidate.config.frameLocalBudgetBytes,
                MaterialFrameLimits(request.candidate.config.maxNoiseOctaveEvaluationsI64))
            val nonUniformBytesI64 = preparedGeometryFootprintV6(request, capabilities, coverage, geometry,
                survivingText, survivingVertices, survivingRects.filterKeys { it !in elided }, captures)
            val sourceFrame = if (captures.isEmpty()) null else PreparedSourceFrameMetadata.of(SizeI32(widthI32, heightI32), capabilities, budget,
                captures.keys.toList(), captures.values.toList(), nonUniformBytesI64).prepare()
            val programs = captures.keys.associateWith { index -> GPUPreparedMaterialProgram.fromCommonSource(requireNotNull(sourceFrame), index) }
            val textDraws = survivingText.map { admitted ->
                val index = admitted.operationIndex
                admitted.bind((operations[index] as DisplayOp.DrawText).paint,
                    GPUPreparedTextMaterialPlan(requireNotNull(sourceFrame).table, sourceFrame.ref(index), sourceFrame.blend(index), programs.getValue(index)))
            }
            val textBound = coverage.bind(textDraws)
            val textPreparation = GPUPreparedTextFrameInventoryPreparation.Ready(textBound.inventory,
                textBound.inventory.metrics.copy(rasterNanoseconds = coverage.rasterNanoseconds, packingNanoseconds = coverage.packingNanoseconds),
                emptyList())
            val verticesDraws = survivingVertices.map { admitted ->
                val index = admitted.operationIndex
                val op = operations[index]
                val paint = when (op) { is DisplayOp.DrawVertices -> op.paint; is DisplayOp.DrawMesh -> op.paint; else -> error("Vertices geometry owner") }
                admitted.bind(paint, (op as? DisplayOp.DrawMesh)?.blendMode, request.targetFacts.colorFormat,
                    GPUPreparedVerticesMaterialPlan(requireNotNull(sourceFrame).table, sourceFrame.ref(index), sourceFrame.blend(index), programs.getValue(index)))
            }
            val verticesBound = when (val result = PreparedVerticesFrameInventoryBuilder.build(verticesDraws,
                verticesLimits, request.capabilities, geometry)) {
                is PreparedVerticesFrameInventoryResult.Ready -> result.inventory
                is PreparedVerticesFrameInventoryResult.Refused -> throw IllegalArgumentException(result.code)
            }
            val core = indexed.filter { it.value is DisplayOp.DrawRect && it.index !in elided }.associate { (index, _) ->
                index to EffectiveMaterialPlanner.Result.Ready(requireNotNull(sourceFrame).table, sourceFrame.ref(index), sourceFrame.blend(index))
            }
            return W5hPreparedMaterialFrameV6(sourceFrame, textPreparation,
                verticesBound.withCapturedElisions(elided.filterKeys { index -> verticesGeometry.any { it.operationIndex == index } },
                    verticesGeometry), core, java.util.Collections.unmodifiableSet(LinkedHashSet(elidedIndices)))
        }
    }
}

/** Existing physical geometry/coverage layouts; source buffers/images/stops/noise are added by the common owner. */
private fun preparedGeometryFootprintV6(request: GPUPreparedSurfaceFrameBuildRequest, caps: PlanCapabilitySnapshot,
    text: PreparedTextCoverageInventory, vertices: PreparedVerticesGeometryInventory,
    textGeometry: List<GPUPreparedTextGeometry>, verticesGeometry: List<GPUPreparedVerticesGeometry>,
    rectBounds: Map<Int, org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds>, captures: Map<Int, PreparedSourceCaptureV6>): Long {
    fun aligned(valueI64: Long, alignmentI64: Long) = Math.addExact(valueI64,
        (alignmentI64 - valueI64 % alignmentI64) % alignmentI64)
    val target = request.targetBounds
    fun textureBytes(bounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds): Long =
        Math.multiplyExact(Math.multiplyExact(bounds.width.toLong(), bounds.height.toLong()), 4L)
    val targetI64 = textureBytes(target)
    var bytesI64 = targetI64
    if (request.includeReadback) {
        val readback = GPUReadbackLayoutPlanner().plan(GPUFrameReadbackRequest(request.readbackRequestId,
            target, GPUReadbackPixelFormat.Rgba8Unorm,
            org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation.EncodedPremulSrgb), request.capabilities)
        require(readback is GPUReadbackLayoutPlan.Planned)
        bytesI64 = Math.addExact(bytesI64, readback.stagingDescriptor.minimumBufferBytes)
    }
    // Core's one snapshot is shared serially. Prepared Text/Vertices instead own one
    // exact bounded snapshot per destination-reading packet, as the real task builder does.
    val coreCopies = rectBounds.filterKeys { captures.getValue(it).blend is BlendPlan.DestinationReadV1 }.values.toList()
    if (coreCopies.isNotEmpty()) bytesI64 = Math.addExact(bytesI64, textureBytes(
        preparedCoreDestinationCapacityBoundsV6(coreCopies, requireNotNull(request.capabilities.limits).copyBytesPerRowAlignment)))
    val textByIndex = textGeometry.associateBy { it.operationIndex }
    text.subRunInstances.forEach { (index, instances) ->
        if (captures.getValue(index).blend is BlendPlan.DestinationReadV1) {
            val clip = requireNotNull(textByIndex.getValue(index).coveragePlan.toPreparedScissorBounds(target))
            val bounds = preparedDestinationIntersection(preparedTextDestinationBounds(instances, target), clip, target)
            bytesI64 = Math.addExact(bytesI64, textureBytes(bounds))
        }
    }
    verticesGeometry.filterNot { it.culledByClip }.forEach { geometry ->
        if (captures.getValue(geometry.operationIndex).blend is BlendPlan.DestinationReadV1) {
            val bounds = (geometry.clippedBounds ?: geometry.deviceBounds).preparedVerticesPixelBounds(target)
            val clip = geometry.clipSnapshot.scissorBounds?.preparedVerticesPixelBounds(target) ?: target
            bytesI64 = Math.addExact(bytesI64, textureBytes(preparedDestinationIntersection(bounds, clip, target)))
        }
    }
    text.pages.forEach { page ->
        val stagingI64 = Math.multiplyExact(preparedR8StagingRowBytesI64(page.width,
            requireNotNull(request.capabilities.limits).copyBytesPerRowAlignment), page.height.toLong())
        bytesI64 = Math.addExact(bytesI64, Math.addExact(stagingI64,
            Math.multiplyExact(page.width.toLong(), page.height.toLong())))
    }
    bytesI64 = Math.addExact(bytesI64, text.metrics.instanceBytes.toLong())
    bytesI64 = Math.addExact(bytesI64, Math.multiplyExact(preparedTextDrawUniformStrideBytesI64(
        caps.minUniformBufferOffsetAlignment.toLong()), text.metrics.subRunCount.toLong()))
    val resourcePlans = vertices.artifacts.values.map { buildVerticesFrameResourcePlan(it, request.deviceGeneration.value) }
    if (resourcePlans.isNotEmpty()) {
        bytesI64 = Math.addExact(bytesI64, buildVerticesStagingLayout(resourcePlans).totalBytes)
        resourcePlans.forEach { plan -> bytesI64 = Math.addExact(bytesI64, Math.addExact(plan.vertexBuffer.byteCount,
            plan.indexBuffer?.let { preparedVerticesIndexBufferBytesI64(it.byteCount) } ?: 0L)) }
    }
    bytesI64 = Math.addExact(bytesI64, preparedVerticesDrawUniformBytesI64(verticesGeometry.count { !it.culledByClip }))
    val firstWritingBlend = captures.values.firstOrNull()?.blend
    val initializationI32 = if (firstWritingBlend == null || firstWritingBlend is BlendPlan.DestinationReadV1) 1 else 0
    val coreCountI64 = Math.addExact(rectBounds.size, initializationI32).toLong()
    if (coreCountI64 != 0L) {
        for ((kind, perDrawI64) in listOf(PlanScratchBufferKind.Vertex to 32L, PlanScratchBufferKind.Index to 24L,
            PlanScratchBufferKind.Uniform to aligned(32L, caps.minUniformBufferOffsetAlignment.toLong()))) {
            val reservedI64 = requireNotNull(caps.bufferAllocationPolicy.reserve(kind, Math.multiplyExact(coreCountI64, perDrawI64)))
            require(reservedI64 <= caps.maxBufferSizeBytes)
            bytesI64 = Math.addExact(bytesI64, reservedI64)
        }
    }
    return bytesI64
}
