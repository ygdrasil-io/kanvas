package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlanner
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
import org.graphiks.kanvas.gpu.renderer.recording.colorGlyphScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.preparedColorGlyphBlendPlan
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
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/**
 * Task-10 legacy native fixture only.
 *
 * Task 8 production no longer owns this topology. These tests retain it until Task 10 migrates the
 * native materializer to the common R8 upload and instance-buffer contracts.
 */
internal fun buildLegacyNativeColorGlyphTaskList(
    frameId: GPUFrameID,
    recordingId: GPURecordingID,
    capabilities: GPUCapabilities,
    deviceGeneration: GPUDeviceGenerationID,
    target: GPUFrameTargetRef,
    semantic: GPUDrawSemanticPayload.ColorGlyph,
    readbackRequestId: GPUReadbackRequestID,
): GPUTaskList {
    val limits = requireNotNull(capabilities.limits)
    val targetBytes = Math.multiplyExact(
        Math.multiplyExact(semantic.targetBounds.width.toLong(), semantic.targetBounds.height.toLong()),
        4L,
    )
    val vertexBytes = Math.multiplyExact(semantic.vertexData.size.toLong(), 4L)
    val indexBytes = Math.multiplyExact(semantic.indexData.size.toLong(), 4L)
    val uniformBytes = semantic.uniformBytes.size.toLong()
    val atlasBytes = semantic.atlasA8Bytes.size.toLong()
    val readbackRequest = GPUFrameReadbackRequest(
        requestId = readbackRequestId,
        sourceBounds = semantic.targetBounds,
        pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
        outputColorInterpretation = GPUColorInterpretation.EncodedPremulSrgb,
    )
    val readbackPlan = requireNotNull(
        GPUReadbackLayoutPlanner().plan(readbackRequest, capabilities)
            as? GPUReadbackLayoutPlan.Planned,
    )
    val suffix = "${frameId.value}.${semantic.payloadRef.commandIdValue}"
    val atlas = GPUFrameTextureRef("texture.color-glyph.atlas.$suffix")
    val vertex = GPUFrameBufferRef("buffer.color-glyph.vertices.$suffix")
    val index = GPUFrameBufferRef("buffer.color-glyph.indices.$suffix")
    val uniform = GPUFrameBufferRef("buffer.color-glyph.uniform.$suffix")
    val staging = GPUFrameBufferRef("buffer.color-glyph.readback.$suffix")
    val prepareTaskId = GPUTaskID("task.color-glyph.prepare.$suffix")
    val renderTaskId = GPUTaskID("task.color-glyph.render.$suffix")
    val readbackTaskId = GPUTaskID("task.color-glyph.readback.$suffix")
    fun buffer(
        resource: GPUFrameBufferRef,
        bytes: Long,
        alignment: Long,
        role: GPUFrameResourceRole,
        usage: GPUFrameResourceUsage,
        label: String,
    ) = GPUResourcePreparationRequest(
        resource = resource,
        descriptor = GPUFrameBufferDescriptor(bytes, alignment),
        role = role,
        usages = setOf(usage, GPUFrameResourceUsage.CopyDestination),
        lifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize = bytes,
        diagnosticLabel = "color-glyph.$label",
    )
    val preparations = listOf(
        GPUResourcePreparationRequest(
            resource = target,
            descriptor = GPUFrameTextureDescriptor(
                semantic.targetBounds,
                GPUColorFormat("rgba8unorm"),
                1,
            ),
            role = GPUFrameResourceRole.SceneTarget,
            usages = setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ),
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
            usages = setOf(
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceUsage.CopyDestination,
            ),
            lifetime = GPUFrameResourceLifetime.SharedCache,
            byteSize = atlasBytes,
            diagnosticLabel = "color-glyph.atlas",
        ),
        buffer(vertex, vertexBytes, 4L, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, "vertices"),
        buffer(index, indexBytes, 4L, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, "indices"),
        buffer(uniform, uniformBytes, 16L, GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, "uniform"),
        GPUResourcePreparationRequest(
            resource = staging,
            descriptor = GPUFrameBufferDescriptor(
                readbackPlan.stagingDescriptor.minimumBufferBytes,
                4L,
            ),
            role = GPUFrameResourceRole.ReadbackStaging,
            usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = readbackPlan.stagingDescriptor.minimumBufferBytes,
            diagnosticLabel = "color-glyph.readback",
        ),
    )
    val allocations = listOf(
        GPUFrameMemoryAllocation("color-glyph.scene-target", GPUFrameMemoryCategory.CanonicalTarget, targetBytes, GPUFrameMemoryResourceKind.Texture2D, semantic.targetBounds),
        GPUFrameMemoryAllocation("color-glyph.atlas.shared-cache-replacement-peak", GPUFrameMemoryCategory.ReusableScratch, Math.multiplyExact(atlasBytes, 2L), GPUFrameMemoryResourceKind.Texture2D, GPUPixelBounds(0, 0, semantic.atlasWidth, semantic.atlasHeight)),
        GPUFrameMemoryAllocation("color-glyph.vertices", GPUFrameMemoryCategory.ReusableScratch, vertexBytes, GPUFrameMemoryResourceKind.Buffer, null),
        GPUFrameMemoryAllocation("color-glyph.indices", GPUFrameMemoryCategory.ReusableScratch, indexBytes, GPUFrameMemoryResourceKind.Buffer, null),
        GPUFrameMemoryAllocation("color-glyph.uniform", GPUFrameMemoryCategory.ReusableScratch, uniformBytes, GPUFrameMemoryResourceKind.Buffer, null),
        GPUFrameMemoryAllocation("color-glyph.readback", GPUFrameMemoryCategory.ReadbackStaging, readbackPlan.stagingDescriptor.minimumBufferBytes, GPUFrameMemoryResourceKind.Buffer, null),
    )
    val packet = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.color-glyph.${semantic.payloadRef.commandIdValue}"),
        commandIdValue = semantic.payloadRef.commandIdValue,
        analysisRecordId = "analysis.color-glyph.${semantic.payloadRef.commandIdValue}",
        passId = "pass.color-glyph.${semantic.payloadRef.commandIdValue}",
        layerId = "root",
        bindingListId = "bindings.color-glyph.${semantic.payloadRef.commandIdValue}",
        insertionReasonCode = "color-glyph-colrv0",
        sortKey = semantic.payloadRef.commandIdValue.toLong(),
        sortKeyPreimage = "paint-order:${semantic.payloadRef.commandIdValue}",
        renderStepId = GPURenderStepID(COLOR_GLYPH_RENDER_STEP_IDENTITY),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        blendPlan = preparedColorGlyphBlendPlan(),
        renderPipelineKey = COLOR_GLYPH_RENDER_PIPELINE_KEY,
        bindingLayoutHash = COLOR_GLYPH_BINDING_LAYOUT_HASH,
        uniformSlot = semantic.payloadRef.uniformSlot,
        semanticPayload = semantic,
        vertexSourceLabel = COLOR_GLYPH_VERTEX_SOURCE_LABEL,
        scissorBoundsHash = colorGlyphScissorAuthority(semantic.scissorBounds),
        targetStateHash = COLOR_GLYPH_TARGET_STATE_HASH,
        originalPaintOrder = semantic.payloadRef.commandIdValue,
        resourceGeneration = semantic.planArtifactKey.generation.value.toLong(),
    )
    val prepare = GPUTask.PrepareResources(
        prepareTaskId,
        recordingId,
        GPUTaskPhase.Prepare,
        preparations,
    )
    val render = GPUTask.Render(
        taskId = renderTaskId,
        recordingId = recordingId,
        phase = GPUTaskPhase.Render,
        target = target,
        loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "opaque-black"),
        samplePlan = GPUSamplePlan.SingleSampleFrame,
        resourceUses = listOf(
            GPUFrameResourceUse(atlas, GPUFrameResourceRole.GlyphAtlas, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.SharedCache, false),
            GPUFrameResourceUse(vertex, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, GPUFrameResourceLifetime.FrameLocal, false),
            GPUFrameResourceUse(index, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, GPUFrameResourceLifetime.FrameLocal, false),
            GPUFrameResourceUse(uniform, GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, GPUFrameResourceLifetime.FrameLocal, false),
        ),
        drawPackets = listOf(packet),
        batchEligibilityByPacketId = mapOf(
            packet.packetId to GPUPassBatchEligibility(
                kind = GPUPassBatchKind.Isolated,
                queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
            ),
        ),
    )
    val readback = GPUTask.Readback(
        readbackTaskId,
        recordingId,
        GPUTaskPhase.Readback,
        target,
        staging,
        readbackRequest,
    )
    val dependencies = listOf(
        GPUTaskDependency(
            prepareTaskId,
            renderTaskId,
            "prepared-color-glyph-order",
            GPUTaskUseToken("prepared-color-glyph.0"),
            reasonCode = "preserve.prepared-color-glyph.order",
        ),
        GPUTaskDependency(
            renderTaskId,
            readbackTaskId,
            "prepared-color-glyph-order",
            GPUTaskUseToken("prepared-color-glyph.1"),
            reasonCode = "preserve.prepared-color-glyph.order",
        ),
    )
    val seal = GPUFrameCapabilitySeal.capture(frameId, deviceGeneration, capabilities)
    val replayHash = "color-glyph:${semantic.canonicalHash}"
    return GPUTaskList(
        frameId = frameId,
        capabilitySeal = seal,
        recordingSeals = listOf(
            GPURecordingSeal(recordingId, 0L, replayHash, replayHash, seal.sealHash),
        ),
        expectedReplayKeyHash = replayHash,
        tasks = listOf(prepare, render, readback),
        dependencies = dependencies,
        phaseOrder = GPUTaskPhase.entries,
        memoryBudget = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(allocations, 1L shl 30, limits),
        ),
    )
}
