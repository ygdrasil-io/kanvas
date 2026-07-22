package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlanner
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.colorGlyphScissorAuthority

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.uuid.Uuid

class GPUColorGlyphPreparedFrameSmokeTest {
    @Test
    fun `prepared frame renders canonical two layer color glyph in one encoder submit and readback`() {
        val semantic = canonicalSemantic()

        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val requestId = GPUReadbackRequestID("readback.color-glyph.prepared")
        val tasks = colorGlyphTaskList(capabilities, generation, semantic, requestId)
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET_WIDTH, TARGET_HEIGHT, "rgba8unorm"),
        )
        try {
            val terminal = session.renderFrame(
                tasks,
                GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
            )
            val rgba = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
            assertContentEquals(
                byteArrayOf(
                    255.toByte(), 0, 0, 255.toByte(),
                    255.toByte(), 0, 0, 255.toByte(),
                    0, 0, 0, 255.toByte(),
                    0, 0, 0, 255.toByte(),
                    0, 0, 0, 255.toByte(),
                    0, 0, 0, 255.toByte(),
                    0, 0, 128.toByte(), 255.toByte(),
                    0, 0, 128.toByte(), 255.toByte(),
                ),
                rgba,
            )

            val counters = session.nativeCounters()
            assertEquals(1L, counters.encoders)
            assertEquals(1L, counters.submits)
            assertEquals(1L, counters.readbackCopies)
            assertEquals(1L, counters.nativePayloadRegistrations)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `prepared completion-only color glyph uses five resources and performs no readback copy`() {
        val semantic = canonicalSemantic()
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val tasks = colorGlyphTaskList(capabilities, generation, semantic, requestId = null)
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET_WIDTH, TARGET_HEIGHT, "rgba8unorm"),
        )
        try {
            val terminal = session.renderFrame(
                tasks,
                GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
            )
            assertIs<GPUSceneFrameOutput.CurrentFrameCompletionOnly>(terminal.output)
            val counters = session.nativeCounters()
            assertEquals(1L, counters.encoders)
            assertEquals(1L, counters.submits)
            assertEquals(0L, counters.readbackCopies)
            assertEquals(1L, counters.nativePayloadRegistrations)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `prepared session reuses color glyph native invariants and atlas across two completed frames`() {
        val semantic = canonicalSemantic()
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val tasks = colorGlyphTaskList(capabilities, generation, semantic, requestId = null)
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET_WIDTH, TARGET_HEIGHT, "rgba8unorm"),
        )
        try {
            repeat(2) { frame ->
                val terminal = session.renderFrame(tasks).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                assertEquals(
                    GPUFrameStructuralOutcome.Succeeded,
                    terminal.outcome,
                    "frame=$frame ${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
                )
            }

            val counters = session.nativeCounters()
            assertEquals(2L, counters.encoders)
            assertEquals(2L, counters.submits)
            assertEquals(1L, counters.colorGlyphInvariantCreations)
            assertEquals(1L, counters.colorGlyphAtlasCreations)
            assertEquals(1L, counters.colorGlyphAtlasUploads)
            assertEquals(1L, counters.colorGlyphAtlasReuses)
            assertEquals(0L, counters.colorGlyphAtlasInvalidations)
            assertEquals(ATLAS_BYTES, counters.colorGlyphCurrentAtlasBytes)
            assertEquals(ATLAS_BYTES, counters.colorGlyphPeakAtlasBytes)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `prepared session invalidates only the color glyph atlas when its generation changes`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val semantics = listOf(
            canonicalSemantic(),
            canonicalSemantic(
                atlasGeneration = ATLAS_GENERATION + 1L,
                atlasBytes = byteArrayOf(64, 192.toByte()),
            ),
        )
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET_WIDTH, TARGET_HEIGHT, "rgba8unorm"),
        )
        try {
            semantics.forEachIndexed { frame, semantic ->
                val terminal = session.renderFrame(
                    colorGlyphTaskList(capabilities, generation, semantic, requestId = null),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                assertEquals(
                    GPUFrameStructuralOutcome.Succeeded,
                    terminal.outcome,
                    "frame=$frame ${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
                )
            }

            val counters = session.nativeCounters()
            assertEquals(2L, counters.encoders)
            assertEquals(2L, counters.submits)
            assertEquals(1L, counters.colorGlyphInvariantCreations)
            assertEquals(2L, counters.colorGlyphAtlasCreations)
            assertEquals(2L, counters.colorGlyphAtlasUploads)
            assertEquals(0L, counters.colorGlyphAtlasReuses)
            assertEquals(1L, counters.colorGlyphAtlasInvalidations)
            assertEquals(ATLAS_BYTES, counters.colorGlyphCurrentAtlasBytes)
            assertEquals(ATLAS_BYTES * 2L, counters.colorGlyphPeakAtlasBytes)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `mutated color glyph plan generation is refused before encoder creation`() {
        val semantic = canonicalSemantic()
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val tasks = colorGlyphTaskList(
            capabilities,
            generation,
            semantic,
            requestId = null,
            packetResourceGeneration = PLAN_GENERATION + 1L,
        )
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET_WIDTH, TARGET_HEIGHT, "rgba8unorm"),
        )
        try {
            val terminal = session.renderFrame(tasks).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(GPUFrameStructuralOutcome.Refused, terminal.outcome)
            assertEquals("stale.preflight.resource_generation", assertNotNull(terminal.diagnostic).code.value)
            assertEquals(0L, session.nativeCounters().encoders)
            assertEquals(0L, session.nativeCounters().nativePayloadRegistrations)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `mutated color glyph packet authority is refused by preflight before native materialization`() {
        val semantic = canonicalSemantic()
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val cases = listOf(
            "render-pipeline" to colorGlyphTaskList(
                capabilities,
                generation,
                semantic,
                requestId = null,
                renderPipelineKeyLabel = "pipeline.color-glyph.substituted",
            ),
            "binding-layout" to colorGlyphTaskList(
                capabilities,
                generation,
                semantic,
                requestId = null,
                bindingLayoutHash = "layout.color-glyph.substituted",
            ),
            "vertex-source" to colorGlyphTaskList(
                capabilities,
                generation,
                semantic,
                requestId = null,
                vertexSourceLabel = "color-glyph-substituted-quad",
            ),
            "target-state" to colorGlyphTaskList(
                capabilities,
                generation,
                semantic,
                requestId = null,
                targetStateHash = "target.rgba8unorm.substituted",
            ),
            "scissor" to colorGlyphTaskList(
                capabilities,
                generation,
                semantic,
                requestId = null,
                scissorBoundsHash = "scissor.substituted",
            ),
            "load-op" to colorGlyphTaskList(
                capabilities,
                generation,
                semantic,
                requestId = null,
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store, null),
            ),
            "store-op" to colorGlyphTaskList(
                capabilities,
                generation,
                semantic,
                requestId = null,
                loadStore = GPULoadStorePlan("clear", GPUStorePlan.Discard, "opaque-black"),
            ),
            "clear-color" to colorGlyphTaskList(
                capabilities,
                generation,
                semantic,
                requestId = null,
                loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent-black"),
            ),
        )
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET_WIDTH, TARGET_HEIGHT, "rgba8unorm"),
        )
        try {
            cases.forEach { (case, tasks) ->
                val terminal = session.renderFrame(tasks).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

                assertEquals(GPUFrameStructuralOutcome.Refused, terminal.outcome, case)
                val diagnostic = assertNotNull(terminal.diagnostic, case)
                assertEquals("invalid.preflight.color_glyph_packet_authority", diagnostic.code.value, case)
                assertEquals(0L, session.nativeCounters().encoders, case)
                assertEquals(0L, session.nativeCounters().nativePayloadRegistrations, case)
            }
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    private fun canonicalSemantic(
        atlasGeneration: Long = ATLAS_GENERATION,
        atlasBytes: ByteArray = byteArrayOf(255.toByte(), 128.toByte()),
    ): org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.ColorGlyph {
        val layers = listOf(
            GPUColorGlyphLayerPayloadInput(
                planArtifactKey = planArtifactKey(),
                layerGlyphID = 11u,
                paletteIndex = 0,
                atlasBounds = GPUPixelBounds(0, 0, 1, 1),
                deviceBounds = GPUPixelBounds(0, 0, 2, 1),
                premultipliedRgba = floatArrayOf(1f, 0f, 0f, 1f),
                useForeground = false,
                foregroundResolved = true,
                placementProof = placementProof(11, GPUPixelBounds(0, 0, 1, 1), atlasGeneration),
            ),
            GPUColorGlyphLayerPayloadInput(
                planArtifactKey = planArtifactKey(),
                layerGlyphID = 12u,
                paletteIndex = 0,
                atlasBounds = GPUPixelBounds(1, 0, 2, 1),
                deviceBounds = GPUPixelBounds(2, 1, 4, 2),
                premultipliedRgba = floatArrayOf(0f, 0f, 1f, 1f),
                useForeground = false,
                foregroundResolved = true,
                placementProof = placementProof(12, GPUPixelBounds(1, 0, 2, 1), atlasGeneration),
            ),
        )
        val vertices = floatArrayOf(
            0f, 0f, 0f, 0f,
            TARGET_WIDTH.toFloat(), 0f, 1f, 0f,
            TARGET_WIDTH.toFloat(), TARGET_HEIGHT.toFloat(), 1f, 1f,
            0f, TARGET_HEIGHT.toFloat(), 0f, 1f,
        )
        val indices = intArrayOf(0, 1, 2, 0, 2, 3)
        val uniformBytes = buildUniformBytes(layers)
        return GPUColorGlyphPayloadGatherer().gatherSemantic(
            commandIdValue = 41,
            renderStepIdentity = COLOR_GLYPH_STEP,
            planArtifactKey = planArtifactKey(),
            atlasArtifactKey = atlasArtifactKey(atlasGeneration),
            atlasA8Bytes = atlasBytes,
            atlasWidth = 2,
            atlasHeight = 1,
            atlasFormat = "r8unorm",
            atlasGeneration = atlasGeneration,
            layers = layers,
            vertexData = vertices,
            indexData = indices,
            uniformBytes = uniformBytes,
            targetBounds = GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT),
            scissorBounds = GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT),
        )
    }

    private fun colorGlyphTaskList(
        capabilities: GPUCapabilities,
        generation: GPUDeviceGenerationID,
        semantic: org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.ColorGlyph,
        requestId: GPUReadbackRequestID?,
        packetResourceGeneration: Long = PLAN_GENERATION,
        renderPipelineKeyLabel: String = "pipeline.color-glyph.rgba8unorm.src-over",
        bindingLayoutHash: String = "layout.color-glyph.group0.uniform-atlas-sampler",
        vertexSourceLabel: String = "color-glyph-indexed-quad",
        scissorBoundsHash: String = colorGlyphScissorAuthority(semantic.scissorBounds),
        targetStateHash: String = "target.rgba8unorm.single-sample",
        loadStore: GPULoadStorePlan = GPULoadStorePlan("clear", GPUStorePlan.Store, "opaque-black"),
    ): GPUTaskList {
        val frameId = GPUFrameID(10_521L)
        val recordingId = GPURecordingID("recording.color-glyph.prepared")
        val seal = GPUFrameCapabilitySeal.capture(frameId, generation, capabilities)
        val readbackRequest = requestId?.let { readbackRequestId ->
            GPUFrameReadbackRequest(
                readbackRequestId,
                GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT),
                GPUReadbackPixelFormat.Rgba8Unorm,
                GPUColorInterpretation("srgb-premul"),
            )
        }
        val readbackBytes = readbackRequest?.let { request ->
            assertIs<GPUReadbackLayoutPlan.Planned>(
                GPUReadbackLayoutPlanner().plan(request, capabilities),
            ).stagingDescriptor.minimumBufferBytes
        }
        val packet = GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.color-glyph.prepared"),
            commandIdValue = 41,
            analysisRecordId = "analysis.color-glyph.prepared",
            passId = "pass.color-glyph.prepared",
            layerId = "root",
            bindingListId = "bindings.color-glyph.prepared",
            insertionReasonCode = "color-glyph-colrv0",
            sortKey = 41L,
            sortKeyPreimage = "paint-order:41",
            renderStepId = GPURenderStepID(COLOR_GLYPH_STEP),
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
            renderPipelineKey = GPURenderPipelineKey(renderPipelineKeyLabel),
            bindingLayoutHash = bindingLayoutHash,
            uniformSlot = semantic.payloadRef.uniformSlot,
            semanticPayload = semantic,
            vertexSourceLabel = vertexSourceLabel,
            scissorBoundsHash = scissorBoundsHash,
            targetStateHash = targetStateHash,
            originalPaintOrder = 41,
            resourceGeneration = packetResourceGeneration,
        )
        val prepare = GPUTask.PrepareResources(
            taskId = PREPARE_TASK,
            recordingId = recordingId,
            phase = GPUTaskPhase.Prepare,
            requests = listOf(
                GPUResourcePreparationRequest(
                    resource = TARGET,
                    descriptor = GPUFrameTextureDescriptor(
                        GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT),
                        GPUColorFormat("rgba8unorm"),
                        1,
                    ),
                    role = GPUFrameResourceRole.SceneTarget,
                    usages = setOf(
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceUsage.CopySource,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = TARGET_BYTES,
                    diagnosticLabel = "color-glyph.scene-target",
                ),
                GPUResourcePreparationRequest(
                    resource = ATLAS,
                    descriptor = GPUFrameTextureDescriptor(
                        GPUPixelBounds(0, 0, 2, 1),
                        GPUColorFormat("r8unorm"),
                        1,
                    ),
                    role = GPUFrameResourceRole.GlyphAtlas,
                    usages = setOf(
                        GPUFrameResourceUsage.TextureBinding,
                        GPUFrameResourceUsage.CopyDestination,
                    ),
                    lifetime = GPUFrameResourceLifetime.SharedCache,
                    byteSize = ATLAS_BYTES,
                    diagnosticLabel = "color-glyph.atlas",
                ),
                GPUResourcePreparationRequest(
                    resource = VERTEX_BUFFER,
                    descriptor = GPUFrameBufferDescriptor(VERTEX_BYTES, 4L),
                    role = GPUFrameResourceRole.VertexData,
                    usages = setOf(GPUFrameResourceUsage.Vertex, GPUFrameResourceUsage.CopyDestination),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = VERTEX_BYTES,
                    diagnosticLabel = "color-glyph.vertices",
                ),
                GPUResourcePreparationRequest(
                    resource = INDEX_BUFFER,
                    descriptor = GPUFrameBufferDescriptor(INDEX_BYTES, 4L),
                    role = GPUFrameResourceRole.IndexData,
                    usages = setOf(GPUFrameResourceUsage.Index, GPUFrameResourceUsage.CopyDestination),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = INDEX_BYTES,
                    diagnosticLabel = "color-glyph.indices",
                ),
                GPUResourcePreparationRequest(
                    resource = UNIFORM_BUFFER,
                    descriptor = GPUFrameBufferDescriptor(UNIFORM_BYTES, 16L),
                    role = GPUFrameResourceRole.UniformData,
                    usages = setOf(GPUFrameResourceUsage.Uniform, GPUFrameResourceUsage.CopyDestination),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = UNIFORM_BYTES,
                    diagnosticLabel = "color-glyph.uniform",
                ),
                *listOfNotNull(readbackBytes?.let { stagingBytes ->
                    GPUResourcePreparationRequest(
                    resource = STAGING,
                    descriptor = GPUFrameBufferDescriptor(stagingBytes, 4L),
                    role = GPUFrameResourceRole.ReadbackStaging,
                    usages = setOf(
                        GPUFrameResourceUsage.CopyDestination,
                        GPUFrameResourceUsage.MapRead,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = stagingBytes,
                    diagnosticLabel = "color-glyph.readback",
                    )
                }).toTypedArray(),
            ),
        )
        val render = GPUTask.Render(
            taskId = RENDER_TASK,
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = TARGET,
            loadStore = loadStore,
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            resourceUses = listOf(
                GPUFrameResourceUse(
                    ATLAS,
                    GPUFrameResourceRole.GlyphAtlas,
                    GPUFrameResourceUsage.TextureBinding,
                    GPUFrameResourceLifetime.SharedCache,
                    write = false,
                ),
                GPUFrameResourceUse(
                    VERTEX_BUFFER,
                    GPUFrameResourceRole.VertexData,
                    GPUFrameResourceUsage.Vertex,
                    GPUFrameResourceLifetime.FrameLocal,
                    write = false,
                ),
                GPUFrameResourceUse(
                    INDEX_BUFFER,
                    GPUFrameResourceRole.IndexData,
                    GPUFrameResourceUsage.Index,
                    GPUFrameResourceLifetime.FrameLocal,
                    write = false,
                ),
                GPUFrameResourceUse(
                    UNIFORM_BUFFER,
                    GPUFrameResourceRole.UniformData,
                    GPUFrameResourceUsage.Uniform,
                    GPUFrameResourceLifetime.FrameLocal,
                    write = false,
                ),
            ),
            drawPackets = listOf(packet),
            batchEligibilityByPacketId = mapOf(
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.Isolated,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
        val readback = readbackRequest?.let { request -> GPUTask.Readback(
            taskId = READBACK_TASK,
            recordingId = recordingId,
            phase = GPUTaskPhase.Readback,
            source = TARGET,
            staging = STAGING,
            request = request,
        ) }
        return GPUTaskList(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(recordingId, 0, "color-glyph", "replay", seal.sealHash),
            ),
            expectedReplayKeyHash = "replay",
            tasks = listOfNotNull(prepare, render, readback),
            dependencies = buildList {
                add(dependency(PREPARE_TASK, RENDER_TASK, 0))
                if (readback != null) add(dependency(RENDER_TASK, READBACK_TASK, 1))
            },
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = GPUFrameMemoryBudgetPlan(
                peakFrameTransientBytes = (readbackBytes ?: 0L) +
                    ATLAS_BYTES * 2L + VERTEX_BYTES + INDEX_BYTES + UNIFORM_BYTES,
                targetResidentBytes = TARGET_BYTES,
                categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
                deviceLimitFacts = emptyList(),
                configuredAggregateBudgetBytes = 1L shl 30,
                diagnostic = null,
            ),
        )
    }

    private fun dependency(from: GPUTaskID, to: GPUTaskID, index: Int) = GPUTaskDependency(
        fromTaskId = from,
        toTaskId = to,
        dependencyKind = "prepared-color-glyph-order",
        useToken = GPUTaskUseToken("prepared-color-glyph.$index"),
        reasonCode = "preserve.prepared-color-glyph.order",
    )

    private fun buildUniformBytes(layers: List<GPUColorGlyphLayerPayloadInput>): ByteArray =
        ByteBuffer.allocate(UNIFORM_BYTES.toInt()).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(TARGET_WIDTH.toFloat())
        putFloat(TARGET_HEIGHT.toFloat())
        putInt(2)
        putInt(0)
        putColor(1f, 0f, 0f, 1f)
        putColor(0f, 0f, 1f, 1f)
        repeat(14) { putColor(0f, 0f, 0f, 0f) }
        layers.forEach { layer -> putAtlasRect(layer.atlasBounds) }
        repeat(14) { putAtlasRect(GPUPixelBounds(0, 0, 0, 0)) }
        layers.forEach { layer -> putDeviceRect(layer.deviceBounds) }
        repeat(14) { putDeviceRect(GPUPixelBounds(0, 0, 0, 0)) }
    }.array()

    private fun ByteBuffer.putColor(r: Float, g: Float, b: Float, a: Float) {
        putFloat(r)
        putFloat(g)
        putFloat(b)
        putFloat(a)
    }

    private fun ByteBuffer.putAtlasRect(bounds: GPUPixelBounds) {
        putFloat(bounds.left / 2f)
        putFloat(bounds.top.toFloat())
        putFloat(bounds.width / 2f)
        putFloat(bounds.height.toFloat())
    }

    private fun ByteBuffer.putDeviceRect(bounds: GPUPixelBounds) {
        putFloat(bounds.left.toFloat())
        putFloat(bounds.top.toFloat())
        putFloat(bounds.width.toFloat())
        putFloat(bounds.height.toFloat())
    }

    private fun planArtifactKey() = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440052")),
        generation = GPUTextArtifactGeneration(PLAN_GENERATION.toInt()),
        contentFingerprint = "prepared-color-glyph-plan",
    )

    private fun atlasArtifactKey(generation: Long = ATLAS_GENERATION) = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440053")),
        generation = GPUTextArtifactGeneration(generation.toInt()),
        contentFingerprint = "prepared-color-glyph-atlas",
    )

    private fun placementProof(glyphId: Int, bounds: GPUPixelBounds, atlasGeneration: Long = ATLAS_GENERATION) =
        GPUColorGlyphAtlasPlacementProofInput(
            atlasArtifactKey = atlasArtifactKey(atlasGeneration),
            strikeGlyphId = glyphId,
            strikeSize = 48f,
            strikeSubpixelX = 0,
            strikeSubpixelY = 0,
            atlasBounds = bounds,
        )

    private companion object {
        const val TARGET_WIDTH = 4
        const val TARGET_HEIGHT = 2
        const val TARGET_BYTES = 32L
        const val ATLAS_BYTES = 2L
        const val VERTEX_BYTES = 64L
        const val INDEX_BYTES = 24L
        const val UNIFORM_BYTES = 784L
        const val ATLAS_GENERATION = 2L
        const val PLAN_GENERATION = 7L
        const val COLOR_GLYPH_STEP = COLOR_GLYPH_RENDER_STEP_IDENTITY
        val TARGET = GPUFrameTargetRef("target.color-glyph.prepared")
        val ATLAS = GPUFrameTextureRef("texture.color-glyph.atlas")
        val VERTEX_BUFFER = GPUFrameBufferRef("buffer.color-glyph.vertices")
        val INDEX_BUFFER = GPUFrameBufferRef("buffer.color-glyph.indices")
        val UNIFORM_BUFFER = GPUFrameBufferRef("buffer.color-glyph.uniform")
        val STAGING = GPUFrameBufferRef("buffer.color-glyph.readback")
        val PREPARE_TASK = GPUTaskID("task.color-glyph.prepare")
        val RENDER_TASK = GPUTaskID("task.color-glyph.render")
        val READBACK_TASK = GPUTaskID("task.color-glyph.readback")
    }
}
