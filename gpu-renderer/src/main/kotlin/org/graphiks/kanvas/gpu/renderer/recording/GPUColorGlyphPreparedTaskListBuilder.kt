package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
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
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Closed typed request for recording one prepared COLRv0 composite frame. */
internal data class GPUColorGlyphPreparedTaskListRequest(
    val frameId: GPUFrameID,
    val recordingId: GPURecordingID,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val semantic: GPUDrawSemanticPayload.ColorGlyph,
    val readbackRequestId: GPUReadbackRequestID?,
    val loadStore: GPULoadStorePlan = GPULoadStorePlan("clear", GPUStorePlan.Store, "opaque-black"),
    val configuredAggregateBudgetBytes: Long = 1L shl 30,
) {
    init {
        require(configuredAggregateBudgetBytes > 0L)
    }
}

/** Recording either succeeds with one immutable task list or refuses before native materialization. */
internal sealed interface GPUColorGlyphPreparedTaskListResult {
    data class Recorded(val taskList: GPUTaskList) : GPUColorGlyphPreparedTaskListResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUColorGlyphPreparedTaskListResult
}

/**
 * Production typed recording seam for the prepared COLRv0 route.
 *
 * The semantic payload remains authoritative. This builder derives only task/resource topology;
 * it never reconstructs atlas, geometry, colors, or generations from labels or hashes.
 */
internal class GPUColorGlyphPreparedTaskListBuilder(
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    fun build(request: GPUColorGlyphPreparedTaskListRequest): GPUColorGlyphPreparedTaskListResult {
        val semantic = request.semantic
        if (semantic.payloadRef.renderStepIdentity != COLOR_GLYPH_RENDER_STEP_IDENTITY ||
            !semantic.hasCanonicalHashIntegrity()
        ) {
            return refused(
                "invalid.recording.color_glyph_semantic",
                "ColorGlyph recording requires the exact canonical immutable semantic payload.",
            )
        }
        if (semantic.targetBounds.left != 0 || semantic.targetBounds.top != 0) {
            return refused(
                "unsupported.recording.color_glyph_target_origin",
                "Prepared ColorGlyph recording currently requires a zero-origin target.",
            )
        }
        val limits = request.capabilities.limits ?: return refused(
            "unsupported.recording.color_glyph_limits_unavailable",
            "Prepared ColorGlyph recording requires observed device limits.",
        )
        val targetBytes = checkedBytes(semantic.targetBounds.width, semantic.targetBounds.height, 4)
            ?: return refused("unsupported.recording.color_glyph_target_size", "ColorGlyph target byte size overflowed.")
        val vertexBytes = checkedCountBytes(semantic.vertexData.size, 4)
            ?: return refused("unsupported.recording.color_glyph_vertex_size", "ColorGlyph vertex byte size overflowed.")
        val indexBytes = checkedCountBytes(semantic.indexData.size, 4)
            ?: return refused("unsupported.recording.color_glyph_index_size", "ColorGlyph index byte size overflowed.")
        val uniformBytes = semantic.uniformBytes.size.toLong()
        val atlasBytes = semantic.atlasA8Bytes.size.toLong()
        val atlasReplacementPeakBytes = colorGlyphSharedAtlasReplacementPeakBytes(atlasBytes)
            ?: return refused(
                "unsupported.recording.color_glyph_atlas_replacement_peak",
                "ColorGlyph shared-atlas replacement peak byte size overflowed.",
            )

        val readbackRequest = request.readbackRequestId?.let { requestId ->
            GPUFrameReadbackRequest(
                requestId = requestId,
                sourceBounds = semantic.targetBounds,
                pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                outputColorInterpretation = GPUColorInterpretation.EncodedPremulSrgb,
            )
        }
        val readbackPlan = readbackRequest?.let { frameReadback ->
            when (val planned = readbackLayoutPlanner.plan(frameReadback, request.capabilities)) {
                is GPUReadbackLayoutPlan.Planned -> planned
                is GPUReadbackLayoutPlan.Refused -> return GPUColorGlyphPreparedTaskListResult.Refused(planned.diagnostic)
            }
        }

        val suffix = "${request.frameId.value}.${semantic.payloadRef.commandIdValue}"
        val atlas = GPUFrameTextureRef("texture.color-glyph.atlas.$suffix")
        val vertex = GPUFrameBufferRef("buffer.color-glyph.vertices.$suffix")
        val index = GPUFrameBufferRef("buffer.color-glyph.indices.$suffix")
        val uniform = GPUFrameBufferRef("buffer.color-glyph.uniform.$suffix")
        val staging = readbackPlan?.let { GPUFrameBufferRef("buffer.color-glyph.readback.$suffix") }
        val prepareTaskId = GPUTaskID("task.color-glyph.prepare.$suffix")
        val renderTaskId = GPUTaskID("task.color-glyph.render.$suffix")
        val readbackTaskId = readbackPlan?.let { GPUTaskID("task.color-glyph.readback.$suffix") }

        val preparations = mutableListOf(
            GPUResourcePreparationRequest(
                resource = request.target,
                descriptor = GPUFrameTextureDescriptor(semantic.targetBounds, GPUColorFormat("rgba8unorm"), 1),
                role = GPUFrameResourceRole.SceneTarget,
                usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = targetBytes,
                diagnosticLabel = "color-glyph.scene-target",
            ),
            GPUResourcePreparationRequest(
                resource = atlas,
                descriptor = GPUFrameTextureDescriptor(
                    GPUPixelBounds(0, 0, semantic.atlasWidth, semantic.atlasHeight),
                    GPUColorFormat("r8unorm"),
                    1,
                ),
                role = GPUFrameResourceRole.GlyphAtlas,
                usages = setOf(GPUFrameResourceUsage.TextureBinding, GPUFrameResourceUsage.CopyDestination),
                lifetime = GPUFrameResourceLifetime.SharedCache,
                byteSize = atlasBytes,
                diagnosticLabel = "color-glyph.atlas",
            ),
            bufferPreparation(vertex, vertexBytes, 4L, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, "vertices"),
            bufferPreparation(index, indexBytes, 4L, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, "indices"),
            bufferPreparation(uniform, uniformBytes, 16L, GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, "uniform"),
        )
        if (readbackPlan != null) {
            preparations += GPUResourcePreparationRequest(
                resource = requireNotNull(staging),
                descriptor = GPUFrameBufferDescriptor(readbackPlan.stagingDescriptor.minimumBufferBytes, 4L),
                role = GPUFrameResourceRole.ReadbackStaging,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = readbackPlan.stagingDescriptor.minimumBufferBytes,
                diagnosticLabel = "color-glyph.readback",
            )
        }

        val allocations = listOf(
            GPUFrameMemoryAllocation("color-glyph.scene-target", GPUFrameMemoryCategory.CanonicalTarget, targetBytes, GPUFrameMemoryResourceKind.Texture2D, semantic.targetBounds),
            GPUFrameMemoryAllocation("color-glyph.atlas.shared-cache-replacement-peak", GPUFrameMemoryCategory.ReusableScratch, atlasReplacementPeakBytes, GPUFrameMemoryResourceKind.Texture2D, GPUPixelBounds(0, 0, semantic.atlasWidth, semantic.atlasHeight)),
            GPUFrameMemoryAllocation("color-glyph.vertices", GPUFrameMemoryCategory.ReusableScratch, vertexBytes, GPUFrameMemoryResourceKind.Buffer, null),
            GPUFrameMemoryAllocation("color-glyph.indices", GPUFrameMemoryCategory.ReusableScratch, indexBytes, GPUFrameMemoryResourceKind.Buffer, null),
            GPUFrameMemoryAllocation("color-glyph.uniform", GPUFrameMemoryCategory.ReusableScratch, uniformBytes, GPUFrameMemoryResourceKind.Buffer, null),
        ) + if (readbackPlan != null) {
            listOf(GPUFrameMemoryAllocation("color-glyph.readback", GPUFrameMemoryCategory.ReadbackStaging, readbackPlan.stagingDescriptor.minimumBufferBytes, GPUFrameMemoryResourceKind.Buffer, null))
        } else emptyList()
        val memoryBudget = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(allocations, request.configuredAggregateBudgetBytes, limits),
        )
        memoryBudget.diagnostic?.let { return GPUColorGlyphPreparedTaskListResult.Refused(it) }

        val packet = colorGlyphPacket(semantic)
        val prepare = GPUTask.PrepareResources(prepareTaskId, request.recordingId, GPUTaskPhase.Prepare, preparations)
        val render = GPUTask.Render(
            taskId = renderTaskId,
            recordingId = request.recordingId,
            phase = GPUTaskPhase.Render,
            target = request.target,
            loadStore = request.loadStore,
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            resourceUses = listOf(
                GPUFrameResourceUse(atlas, GPUFrameResourceRole.GlyphAtlas, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.SharedCache, write = false),
                GPUFrameResourceUse(vertex, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, GPUFrameResourceLifetime.FrameLocal, write = false),
                GPUFrameResourceUse(index, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, GPUFrameResourceLifetime.FrameLocal, write = false),
                GPUFrameResourceUse(uniform, GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, write = false),
            ),
            drawPackets = listOf(packet),
            batchEligibilityByPacketId = mapOf(
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.Isolated,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
        val tasks = mutableListOf<GPUTask>(prepare, render)
        val dependencies = mutableListOf(dependency(prepareTaskId, renderTaskId, 0))
        if (readbackRequest != null && staging != null && readbackTaskId != null) {
            tasks += GPUTask.Readback(
                taskId = readbackTaskId,
                recordingId = request.recordingId,
                phase = GPUTaskPhase.Readback,
                source = request.target,
                staging = staging,
                request = readbackRequest,
            )
            dependencies += dependency(renderTaskId, readbackTaskId, 1)
        }

        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val replayHash = "color-glyph:${semantic.canonicalHash}"
        return GPUColorGlyphPreparedTaskListResult.Recorded(
            GPUTaskList(
                frameId = request.frameId,
                capabilitySeal = seal,
                recordingSeals = listOf(GPURecordingSeal(request.recordingId, 0L, replayHash, replayHash, seal.sealHash)),
                expectedReplayKeyHash = replayHash,
                tasks = tasks,
                dependencies = dependencies,
                phaseOrder = GPUTaskPhase.entries,
                memoryBudget = memoryBudget,
            ),
        )
    }

    private fun colorGlyphPacket(semantic: GPUDrawSemanticPayload.ColorGlyph): GPUDrawPacket {
        val commandId = semantic.payloadRef.commandIdValue
        return GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.color-glyph.$commandId"),
            commandIdValue = commandId,
            analysisRecordId = "analysis.color-glyph.$commandId",
            passId = "pass.color-glyph.$commandId",
            layerId = "root",
            bindingListId = "bindings.color-glyph.$commandId",
            insertionReasonCode = "color-glyph-colrv0",
            sortKey = commandId.toLong(),
            sortKeyPreimage = "paint-order:$commandId",
            renderStepId = GPURenderStepID(COLOR_GLYPH_RENDER_STEP_IDENTITY),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            blendPlan = GPUBlendPlan.FixedFunctionBlend(
                mode = GPUBlendMode.SRC_OVER,
                state = GPUFixedFunctionBlendState(
                    stateId = "one_isa",
                    color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
                    alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
                    writeMask = "rgba",
                ),
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            ),
            renderPipelineKey = COLOR_GLYPH_RENDER_PIPELINE_KEY,
            bindingLayoutHash = COLOR_GLYPH_BINDING_LAYOUT_HASH,
            uniformSlot = semantic.payloadRef.uniformSlot,
            semanticPayload = semantic,
            vertexSourceLabel = COLOR_GLYPH_VERTEX_SOURCE_LABEL,
            scissorBoundsHash = colorGlyphScissorAuthority(semantic.scissorBounds),
            targetStateHash = COLOR_GLYPH_TARGET_STATE_HASH,
            originalPaintOrder = commandId,
            resourceGeneration = semantic.planArtifactKey.generation.value.toLong(),
        )
    }

    private fun bufferPreparation(
        resource: GPUFrameBufferRef,
        bytes: Long,
        alignment: Long,
        role: GPUFrameResourceRole,
        primaryUsage: GPUFrameResourceUsage,
        label: String,
    ) = GPUResourcePreparationRequest(
        resource = resource,
        descriptor = GPUFrameBufferDescriptor(bytes, alignment),
        role = role,
        usages = setOf(primaryUsage, GPUFrameResourceUsage.CopyDestination),
        lifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize = bytes,
        diagnosticLabel = "color-glyph.$label",
    )

    private fun dependency(from: GPUTaskID, to: GPUTaskID, ordinal: Int) = GPUTaskDependency(
        fromTaskId = from,
        toTaskId = to,
        dependencyKind = "prepared-color-glyph-order",
        useToken = GPUTaskUseToken("prepared-color-glyph.$ordinal"),
        reasonCode = "preserve.prepared-color-glyph.order",
    )

    private fun checkedBytes(width: Int, height: Int, bytesPerPixel: Int): Long? = try {
        Math.multiplyExact(Math.multiplyExact(width.toLong(), height.toLong()), bytesPerPixel.toLong())
    } catch (_: ArithmeticException) {
        null
    }

    private fun checkedCountBytes(count: Int, bytesPerElement: Int): Long? = try {
        Math.multiplyExact(count.toLong(), bytesPerElement.toLong())
    } catch (_: ArithmeticException) {
        null
    }

    private fun refused(code: String, message: String) = GPUColorGlyphPreparedTaskListResult.Refused(
        GPUDiagnostic(
            code = GPUDiagnosticCode(code),
            domain = GPUDiagnosticDomain.Recording,
            severity = GPUDiagnosticSeverity.Error,
            message = message,
        ),
    )
}

internal fun colorGlyphSharedAtlasReplacementPeakBytes(atlasBytes: Long): Long? {
    if (atlasBytes <= 0L) return null
    return try {
        Math.multiplyExact(atlasBytes, 2L)
    } catch (_: ArithmeticException) {
        null
    }
}
