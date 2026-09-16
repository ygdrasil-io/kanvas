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
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.payloads.*
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.planning.W5bBlendPlanLowerer
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.geometry.toCompatibilityPath
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.CoverageRequest
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
    val mapping: GPUOpMapping,
    val recording: GPURecording,
    val coreSemantics: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
    val coreGeometryInventory: GPUCorePrimitiveFrameGeometryInventory,
) {
    companion object {
        fun prepare(request: GPUPreparedSurfaceFrameBuildRequest, generationI32: Int): W5hPreparedMaterialFrameV6? {
            val operations = request.candidate.operations
            val indexed = operations.withIndex().filterNot { (_, operation) ->
                operation is DisplayOp.SetTransform || operation is DisplayOp.SetClip || operation is DisplayOp.Annotation
            }
            // Whole-frame closure precedes every source capture, catalogue, source index or owner.
            // Unsupported frame/clip topologies retain their complete historical route.
            if (indexed.any { (_, op) -> when (op) {
                    is DisplayOp.DrawRect -> op.paint.isStroke()
                    is DisplayOp.DrawRRect -> op.paint.isStroke()
                    is DisplayOp.DrawPath, is DisplayOp.DrawPoint, is DisplayOp.DrawPoints, is DisplayOp.DrawImage -> false
                    is DisplayOp.DrawText, is DisplayOp.DrawVertices -> false
                    is DisplayOp.DrawMesh -> op.mesh.program != null
                    else -> true
                } }) return null
            val textGeometry = ArrayList<GPUPreparedTextGeometry>()
            val verticesGeometry = ArrayList<GPUPreparedVerticesGeometry>()
            val widthI32 = request.targetBounds.width
            val heightI32 = request.targetBounds.height
            // This immutable public snapshot is the occurrence identity, not a source owner.
            val draws = indexed.associate { (index, operation) ->
                val scene = DisplayOpSceneAdapter.capture(listOf(operation), SceneExtent(widthI32, heightI32), ColorSpace.SRGB)
                    as? SceneCaptureResult.Captured ?: return null
                index to requireNotNull(scene.scene.singleOrNull() as? SceneCommand.Draw).node
            }
            val state = GPUOpMapper.capturePreparedFrameState(operations)
            val recorder = GPURecorder(request.recordingId, request.frameId, request.capabilities, request.deviceGeneration)
            val coreBuilder = GPUCorePrimitivePreparedFrameTaskListBuilder()
            val coreVisuals = linkedMapOf<Int, GPUCoreSourceGeometryVisual>()
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
                else -> {
                    val projected = if (operation is DisplayOp.DrawImage) {
                        val node = projectW5eImageGeometry(draws.getValue(index))
                        val paint = (operation.paint ?: Paint()).copy(style = PaintStyle.FILL,
                            antiAlias = node.coverage == CoverageRequest.ANTIALIASED)
                        when (val geometry = node.geometry) {
                            is GeometryNode.Rect -> DisplayOp.DrawRect(geometry.copyBounds(), paint, operation.transform, operation.clip)
                            is GeometryNode.Path -> DisplayOp.DrawPath(geometry.path.toCompatibilityPath(), paint, operation.transform, operation.clip)
                            else -> error("Image geometry projection changed family")
                        }
                    } else operation
                    try {
                        recorder.recordSourceGeometry(draws.getValue(index)) { occurrence ->
                            val visual = requireNotNull(GPUOpMapper.lowerSourceFreeCoreVisual(projected, occurrence, index,
                                GPUPreparedImageLoweringContext(state.provenanceByOperationIndex.getValue(index), request.targetFacts,
                                    request.candidate.config, request.capabilities)))
                            coreVisuals[index] = visual
                            visual.visual.normalized
                        }
                    } catch (_: GPUCoreSourceGeometryRefusal) {
                        return null // Preserve the historical whole-frame refusal before source capture.
                    }
                }
            }
            // Check every sibling's topology before catalogue/authentication/publication.
            val emptyTextClips = textGeometry.filter {
                it.coveragePlan is org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.Scissor &&
                    it.coveragePlan.toPreparedScissorBounds(request.targetBounds) == null
            }.mapTo(linkedSetOf()) { it.operationIndex }
            if (coreVisuals.values.any { it.visual.clipExecutionPlan != GPUClipExecutionPlan.NoClip &&
                    it.visual.clipExecutionPlan !is GPUClipExecutionPlan.ScissorOnly } ||
                textGeometry.any { it.operationIndex !in emptyTextClips && it.clipExecution != GPUClipExecutionPlan.NoClip &&
                    it.clipExecution !is GPUClipExecutionPlan.ScissorOnly }) return null
            val sourceAnalysis = recorder.analyzeSourceGeometry()
            val capturedCore = linkedMapOf<Int, GPUCorePrimitiveCapturedGeometry>()
            val coreSourceInventories = linkedMapOf<Int, GPUCorePrimitiveSourceGeometryInventory>()
            coreVisuals.entries.zip(sourceAnalysis.commandGeometry).forEach { (entry, analysis) ->
                // The mapper has already applied the exact visible-bounds predicate. A
                // culled sibling still authenticates its source below, but owns no native
                // geometry, destination snapshot or uniform range.
                if (entry.value.outsideTarget) return@forEach
                val captured = try {
                    GPUCorePrimitiveSemanticBuilder.captureSourceGeometry(entry.value.visual, analysis, request.targetBounds)
                } catch (_: GPUCoreSourceGeometryRefusal) {
                    return null // Whole-frame topology decline, before catalogue or source ownership.
                }
                capturedCore[entry.key] = captured
                when (val inventory = coreBuilder.captureSourceGeometryInventory(captured, request.capabilities,
                    GPUColorFormat(request.targetFacts.colorFormat))) {
                    is GPUCorePrimitiveSourceGeometryInventoryResult.Captured -> coreSourceInventories[entry.key] = inventory.inventory
                    GPUCorePrimitiveSourceGeometryInventoryResult.OutsideDomain -> return null
                    is GPUCorePrimitiveSourceGeometryInventoryResult.Refused -> throw IllegalArgumentException(inventory.code)
                }
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
            val admittedCoverage = when (val result = PreparedTextFrameInventoryBuilder.resolveGeometry(textGeometry,
                GPUTextArtifactGeneration(generationI32), admissionLimits)) {
                is PreparedTextResolvedCoverageInventory -> result
                is PreparedTextFrameInventoryResult.Refused -> throw IllegalArgumentException(result.code)
            }
            // Only actual rasterized sub-runs consume a source. Project empty Text together
            // with its occurrence before capture: it must never acquire a material owner.
            val consumingTextIndices = admittedCoverage.deviceQuadsByOperationIndex.keys
            val zeroConsumerTextIndices = textGeometry.filter { it.operationIndex !in consumingTextIndices }
                .mapTo(linkedSetOf()) { it.operationIndex }
            val verticesLimits = GPUPreparedVerticesFramePreparer.defaultLimits(request.capabilities)
            // These are the mapper's actual clip/coverage predicates, applied to the real
            // CPU inventory before any source exists. Empty clips are topology, not NoOp blends.
            val culledGeometryIndices = LinkedHashSet(emptyTextClips)
            verticesGeometry.filter { it.culledByClip }.mapTo(culledGeometryIndices) { it.operationIndex }
            coreVisuals.filterValues { it.outsideTarget }.keys.forEach(culledGeometryIndices::add)
            // An out-of-target nonempty sub-run is not a representable prepared semantic.
            // Preserve that whole frame's historical admission before catalogue/capture.
            if (admittedCoverage.deviceQuadsByOperationIndex.any { (index, quads) ->
                    index !in culledGeometryIndices && preparedTextQuadBounds(quads, request.targetFacts) == null
                } || verticesGeometry.any { it.operationIndex !in culledGeometryIndices &&
                    (it.clippedBounds ?: it.deviceBounds).preparedVerticesPixelBounds(request.targetBounds).isEmpty }) return null
            val capabilities = when (val result = request.capabilities.toPlanCapabilitySnapshot(request.deviceGeneration)) {
                is GpuPlanCapabilityAdapterResult.Supported -> result.snapshot
                is GpuPlanCapabilityAdapterResult.Unsupported -> throw IllegalArgumentException(result.diagnostic.code.value)
            }
            val domain = RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat())
            val catalog = RuntimeEffectSemanticCatalog.builtinSnapshot()
            val allAuthenticated = indexed.associate { (index, operation) ->
                index to PreparedSourceAuthenticationV6.authenticate(draws.getValue(index),
                    if (operation is DisplayOp.DrawText || coreVisuals[index]?.visual?.geometryCoverage == GPUCoverageConsumption.ScalarCoverage)
                        CoveragePlan.AnalyticScalarAA else CoveragePlan.FullOrScissor,
                    BlendTargetClampV1.UnitInterval, catalog)
            }
            val elided = allAuthenticated.filter { (index, capture) -> capture.blend == BlendPlan.NoOpV1 ||
                index in culledGeometryIndices || index in zeroConsumerTextIndices }
            val elidedIndices = elided.keys + zeroConsumerTextIndices
            val authenticated = allAuthenticated.filterKeys { it !in elided }
            val survivingText = textGeometry.filterNot { it.operationIndex in elidedIndices }
            val survivingVertices = verticesGeometry.filterNot { it.operationIndex in elided }
            val coverage = when (val result = admittedCoverage.pack(textLimits,
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
            val textVisualGeometry = coverage.subRunInstances.groupBy({ it.first }, { it.second }).mapValues { (index, subRuns) ->
                val draw = survivingText.single { it.operationIndex == index }
                subRuns.map { instances ->
                    when (val captured = GPUPreparedTextVisualGeometry.capture(draw, instances, request.targetFacts,
                        request.candidate.config, request.capabilities)) {
                        is GPUPreparedTextVisualGeometryResult.Ready -> captured.geometry
                        else -> error("Authenticated surviving Text geometry changed during packing")
                    }
                }
            }
            val counts = indexed.filterNot { it.index in elidedIndices }.associate { (index, _) ->
                index to (textVisualGeometry[index]?.size ?: 1)
            }
            val sceneClear = counts.isEmpty() || operations.requiresPreparedDstReadSceneClear(
                request.candidate.color.interpretation, counts.keys) { authenticated.getValue(it).blend is BlendPlan.DestinationReadV1 }
            val identities = GPUOpMapper.assignPreparedFrameCommandIdentities(operations, counts, sceneClear, state)
            val clearVisual = if (sceneClear) requireNotNull(GPUOpMapper.lowerPreparedCoreVisual(
                DisplayOp.Clear(ColorARGB.Transparent), GPUDrawCommandID(0), 0,
                GPUPreparedImageLoweringContext(GPUFrameProvenance.None, request.targetFacts, request.candidate.config, request.capabilities))) else null
            val survivingCore = coreVisuals.filterKeys { it !in elidedIndices }
            val ids = identities.commandIdsByOperationIndex
            val finalAnalysis = recorder.bindCommandIdentities(sourceAnalysis,
                survivingCore.map { (index, captured) -> GPURecordedCommandIdentityBinding(captured.visual.normalized, GPUDrawCommandID(ids.getValue(index).single())) },
                coreVisuals.filterKeys { it in elidedIndices }.values.map { it.visual.normalized },
                ids.filterKeys { it !in survivingCore }.values.flatten().toSet(), clearVisual?.normalized as? NormalizedDrawCommand.FillRect)
            val gatherer = GPUCorePrimitivePayloadGatherer()
            val coreGeometry = survivingCore.keys.associate { index ->
                ids.getValue(index).single() to gatherer.bindSourceGeometryIdentity(capturedCore.getValue(index), finalAnalysis)
            }.toMutableMap()
            val sourceInventories = survivingCore.keys.associate { index ->
                ids.getValue(index).single() to coreSourceInventories.getValue(index)
            }.toMutableMap()
            clearVisual?.let { clear ->
                val gathered = GPUCorePrimitiveSemanticBuilder.gatherGeometry(listOf(clear), finalAnalysis, request.targetBounds)
                require(gathered is GPUCorePrimitiveGeometryGatherResult.Gathered)
                val clearGeometry = gathered.plans.getValue(0)
                coreGeometry[0] = clearGeometry
                when (val captured = coreBuilder.captureGeneratedClearGeometryInventory(clearGeometry, finalAnalysis,
                    request.capabilities, GPUColorFormat(request.targetFacts.colorFormat))) {
                    is GPUCorePrimitiveSourceGeometryInventoryResult.Captured -> sourceInventories[0] = captured.inventory
                    else -> error("Generated transparent initialization must retain its historical Core geometry")
                }
            }
            val finalCoreBlends = survivingCore.keys.associate { ids.getValue(it).single() to authenticated.getValue(it).blend }
            val coreInventory = when (val prepared = coreBuilder.prepareGeometryInventory(GPUCorePrimitiveGeometryFrameRequest(
                finalAnalysis, request.capabilities, request.targetBounds, coreGeometry, sourceInventories, finalCoreBlends,
                request.candidate.config.frameLocalBudgetBytes, GPUColorFormat(request.targetFacts.colorFormat),
                (ids.values.flatten() + if (sceneClear) listOf(0) else emptyList()).sorted(), request.target))) {
                is GPUCorePrimitiveGeometryInventoryResult.Prepared -> prepared.inventory
                GPUCorePrimitiveGeometryInventoryResult.OutsideDomain -> throw IllegalArgumentException("unsupported.surface.prepared.common-core-inventory")
                is GPUCorePrimitiveGeometryInventoryResult.Refused -> throw IllegalArgumentException(prepared.code)
            }
            val nonUniformBytesI64 = preparedGeometryFootprintV6(request, capabilities, coverage, geometry,
                survivingText, survivingVertices, coreInventory, authenticated.mapValues { it.value.blend })
            val captures = authenticated.mapValues { it.value.capture(domain) }
            val consumerCaptures = ids.flatMap { (index, commandIds) -> commandIds.map { it to captures.getValue(index) } }
            val sourceFrame = if (captures.isEmpty()) null else PreparedSourceFrameMetadata.of(SizeI32(widthI32, heightI32), capabilities, budget,
                consumerCaptures.map { it.first }, consumerCaptures.map { it.second }, nonUniformBytesI64).prepare()
            val programs = captures.keys.associateWith { index -> GPUPreparedMaterialProgram.fromCommonSource(requireNotNull(sourceFrame), ids.getValue(index).first()) }
            val textDraws = survivingText.map { admitted ->
                val index = admitted.operationIndex
                val commandId = ids.getValue(index).first()
                admitted.bind((operations[index] as DisplayOp.DrawText).paint,
                    GPUPreparedTextMaterialPlan(requireNotNull(sourceFrame).table, sourceFrame.ref(commandId), sourceFrame.blend(commandId), programs.getValue(index)))
            }
            val textBound = coverage.bind(textDraws)
            val textPreparation = GPUPreparedTextFrameInventoryPreparation.Ready(textBound.inventory,
                textBound.inventory.metrics.copy(rasterNanoseconds = coverage.rasterNanoseconds, packingNanoseconds = coverage.packingNanoseconds),
                emptyList())
            val verticesDraws = survivingVertices.map { admitted ->
                val index = admitted.operationIndex
                val commandId = ids.getValue(index).single()
                val op = operations[index]
                val paint = when (op) { is DisplayOp.DrawVertices -> op.paint; is DisplayOp.DrawMesh -> op.paint; else -> error("Vertices geometry owner") }
                admitted.bind(paint, (op as? DisplayOp.DrawMesh)?.blendMode, request.targetFacts.colorFormat,
                    GPUPreparedVerticesMaterialPlan(requireNotNull(sourceFrame).table, sourceFrame.ref(commandId), sourceFrame.blend(commandId), programs.getValue(index)))
            }
            val verticesBound = when (val result = PreparedVerticesFrameInventoryBuilder.build(verticesDraws,
                verticesLimits, request.capabilities, geometry)) {
                is PreparedVerticesFrameInventoryResult.Ready -> result.inventory
                is PreparedVerticesFrameInventoryResult.Refused -> throw IllegalArgumentException(result.code)
            }
            val core = survivingCore.keys.associateWith { index ->
                val commandId = ids.getValue(index).single()
                EffectiveMaterialPlanner.Result.Ready(requireNotNull(sourceFrame).table, sourceFrame.ref(commandId), sourceFrame.blend(commandId))
            }
            val verticesWithElisions = verticesBound.withAuthenticatedElisions(
                elided.filterKeys { index -> verticesGeometry.any { it.operationIndex == index } }, verticesGeometry)
            var boundMapping: GPUOpMapping? = null
            val recording = recorder.bindGeometry(finalAnalysis,
                survivingCore.keys.map { index ->
                    val commandId = ids.getValue(index).single()
                    GPURecordedSourceBinding(GPUDrawCommandID(commandId), draws.getValue(index), requireNotNull(sourceFrame).ref(commandId))
                }, bindPreparedConsumers = { commands ->
                    val commandsById = commands.associateBy { it.commandId.value }
                    val coreBound = survivingCore.mapValues { (index, captured) ->
                        captured.visual.copy(normalized = commandsById.getValue(ids.getValue(index).single()))
                    }
                    val mapping = GPUOpMapper.bindPreparedFrame(identities, coreBound, textVisualGeometry,
                        textPreparation.inventory, verticesWithElisions, clearVisual, request.targetFacts,
                        request.candidate.config, request.capabilities)
                    boundMapping = mapping
                    (mapping.visualCommands.filter { it.preparedText != null }.map { it.normalized } +
                        requireNotNull(mapping.preparedVerticesInventory).normalizedCommands(request.targetFacts, request.capabilities))
                        .sortedBy { it.commandId.value }
                })
            val coreSemantics = survivingCore.map { (index, captured) ->
                val commandId = ids.getValue(index).single()
                commandId to gatherer.bindSourceReference(coreGeometry.getValue(commandId), requireNotNull(sourceFrame).ref(commandId),
                    captured.visual.blendPlan.canonicalIdentity())
            }.toMap().toMutableMap()
            clearVisual?.let { clear -> coreSemantics[0] = coreBuilder.bindGeneratedClear(coreGeometry.getValue(0),
                sourceInventories.getValue(0), clear.normalized as NormalizedDrawCommand.FillRect, clear.blendPlan) }
            val mapping = requireNotNull(boundMapping)
            return W5hPreparedMaterialFrameV6(sourceFrame, textPreparation, requireNotNull(mapping.preparedVerticesInventory), core,
                java.util.Collections.unmodifiableSet(LinkedHashSet(elidedIndices)), mapping, recording, coreSemantics, coreInventory)
        }
    }
}

/** Existing physical geometry/coverage layouts; source buffers/images/stops/noise are added by the common owner. */
private fun preparedGeometryFootprintV6(request: GPUPreparedSurfaceFrameBuildRequest, caps: PlanCapabilitySnapshot,
    text: PreparedTextCoverageInventory, vertices: PreparedVerticesGeometryInventory,
    textGeometry: List<GPUPreparedTextGeometry>, verticesGeometry: List<GPUPreparedVerticesGeometry>,
    coreInventory: GPUCorePrimitiveFrameGeometryInventory, blends: Map<Int, BlendPlan>): Long {
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
    bytesI64 = Math.addExact(bytesI64, coreInventory.physicalFootprintBytesI64(caps))
    val textByIndex = textGeometry.associateBy { it.operationIndex }
    text.subRunInstances.forEach { (index, instances) ->
        if (blends.getValue(index) is BlendPlan.DestinationReadV1) {
            val clip = requireNotNull(textByIndex.getValue(index).coveragePlan.toPreparedScissorBounds(target))
            val bounds = preparedDestinationIntersection(preparedTextDestinationBounds(instances, target), clip, target)
            bytesI64 = Math.addExact(bytesI64, textureBytes(bounds))
        }
    }
    verticesGeometry.filterNot { it.culledByClip }.forEach { geometry ->
        if (blends.getValue(geometry.operationIndex) is BlendPlan.DestinationReadV1) {
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
    return bytesI64
}
