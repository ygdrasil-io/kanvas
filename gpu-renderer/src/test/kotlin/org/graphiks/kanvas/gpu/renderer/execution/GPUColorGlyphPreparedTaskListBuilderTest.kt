package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListResult
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.colorGlyphScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.colorGlyphSharedAtlasReplacementPeakBytes

import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import kotlin.uuid.Uuid

class GPUColorGlyphPreparedTaskListBuilderTest {
    @Test
    fun `typed color glyph recording builds exact completion only task list`() {
        val semantic = semantic()
        val result = GPUColorGlyphPreparedTaskListBuilder().build(
            request(semantic = semantic, readbackRequestId = null),
        )

        val taskList = assertIs<GPUColorGlyphPreparedTaskListResult.Recorded>(result).taskList
        assertEquals(listOf(GPUTask.PrepareResources::class, GPUTask.Render::class), taskList.tasks.map { it::class })
        val prepare = assertIs<GPUTask.PrepareResources>(taskList.tasks[0])
        assertEquals(
            listOf(
                GPUFrameResourceRole.SceneTarget,
                GPUFrameResourceRole.GlyphAtlas,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceRole.UniformData,
            ),
            prepare.requests.map { it.role },
        )
        assertNull(prepare.requests.singleOrNull { it.role == GPUFrameResourceRole.ReadbackStaging })
        assertEquals(
            GPUFrameResourceLifetime.SharedCache,
            prepare.requests.single { it.role == GPUFrameResourceRole.GlyphAtlas }.lifetime,
        )
        val render = assertIs<GPUTask.Render>(taskList.tasks[1])
        val packet = render.drawPackets.single()
        assertSame(semantic, packet.semanticPayload)
        assertEquals(COLOR_GLYPH_RENDER_STEP_IDENTITY, packet.renderStepId.value)
        assertEquals(PLAN_GENERATION, packet.resourceGeneration)
        assertEquals(COLOR_GLYPH_RENDER_PIPELINE_KEY, packet.renderPipelineKey)
        assertEquals(COLOR_GLYPH_BINDING_LAYOUT_HASH, packet.bindingLayoutHash)
        assertEquals(COLOR_GLYPH_VERTEX_SOURCE_LABEL, packet.vertexSourceLabel)
        assertEquals(colorGlyphScissorAuthority(semantic.scissorBounds), packet.scissorBoundsHash)
        assertEquals(COLOR_GLYPH_TARGET_STATE_HASH, packet.targetStateHash)
        assertEquals(GPULoadStorePlan("clear", GPUStorePlan.Store, "opaque-black"), render.loadStore)
        assertEquals(
            listOf(
                GPUFrameResourceRole.GlyphAtlas,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceRole.UniformData,
            ),
            render.resourceUses.map { it.role },
        )
        assertEquals(
            GPUFrameResourceLifetime.SharedCache,
            render.resourceUses.single { it.role == GPUFrameResourceRole.GlyphAtlas }.lifetime,
        )
        assertEquals(1, taskList.dependencies.size)
        assertEquals(876L, taskList.memoryBudget.peakFrameTransientBytes)
        assertEquals(8L, taskList.memoryBudget.targetResidentBytes)
        assertEquals(876L, taskList.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.ReusableScratch))
    }

    @Test
    fun `typed color glyph recording adds exact planned readback resource and dependency`() {
        val semantic = semantic()
        val requestId = GPUReadbackRequestID("readback.color-glyph.builder")
        val result = GPUColorGlyphPreparedTaskListBuilder().build(
            request(semantic = semantic, readbackRequestId = requestId),
        )

        val taskList = assertIs<GPUColorGlyphPreparedTaskListResult.Recorded>(result).taskList
        assertEquals(
            listOf(GPUTask.PrepareResources::class, GPUTask.Render::class, GPUTask.Readback::class),
            taskList.tasks.map { it::class },
        )
        val prepare = assertIs<GPUTask.PrepareResources>(taskList.tasks[0])
        val staging = prepare.requests.single { it.role == GPUFrameResourceRole.ReadbackStaging }
        assertEquals(8L, staging.byteSize)
        val readback = assertIs<GPUTask.Readback>(taskList.tasks[2])
        assertEquals(requestId, readback.request.requestId)
        assertEquals(staging.resource, readback.staging)
        assertEquals(2, taskList.dependencies.size)
        assertEquals(884L, taskList.memoryBudget.peakFrameTransientBytes)
        assertEquals(8L, taskList.memoryBudget.targetResidentBytes)
    }

    @Test
    fun `typed color glyph recording refuses missing observed limits before task creation`() {
        val semantic = semantic()
        val result = GPUColorGlyphPreparedTaskListBuilder().build(
            GPUColorGlyphPreparedTaskListRequest(
                frameId = GPUFrameID(42L),
                recordingId = GPURecordingID("recording.color-glyph.builder"),
                capabilities = capabilities().copy(limits = null),
                deviceGeneration = GPUDeviceGenerationID(9L),
                target = GPUFrameTargetRef("target.color-glyph.builder"),
                semantic = semantic,
                readbackRequestId = null,
            ),
        )

        val refused = assertIs<GPUColorGlyphPreparedTaskListResult.Refused>(result)
        assertEquals("unsupported.recording.color_glyph_limits_unavailable", refused.diagnostic.code.value)
    }

    @Test
    fun `shared atlas replacement peak uses checked signed arithmetic`() {
        assertEquals(4L, colorGlyphSharedAtlasReplacementPeakBytes(2L))
        assertNull(colorGlyphSharedAtlasReplacementPeakBytes(Long.MAX_VALUE))
    }

    @Test
    fun `native materializer refuses every substituted prepared authority before native payload creation`() {
        val semantic = semantic()
        val generation = GPUDeviceGenerationID(9L)
        val base = assertIs<GPUColorGlyphPreparedTaskListResult.Recorded>(
            GPUColorGlyphPreparedTaskListBuilder().build(request(semantic, readbackRequestId = null)),
        ).taskList
        val cases = listOf(
            "render-pipeline" to mutatedTaskList(base) { packet ->
                packet.withAuthority(renderPipelineKey = GPURenderPipelineKey("pipeline.color-glyph.substituted"))
            },
            "binding-layout" to mutatedTaskList(base) { packet ->
                packet.withAuthority(bindingLayoutHash = "layout.color-glyph.substituted")
            },
            "vertex-source" to mutatedTaskList(base) { packet ->
                packet.withAuthority(vertexSourceLabel = "color-glyph-substituted-quad")
            },
            "target-state" to mutatedTaskList(base) { packet ->
                packet.withAuthority(targetStateHash = "target.rgba8unorm.substituted")
            },
            "scissor" to mutatedTaskList(base) { packet ->
                packet.withAuthority(scissorBoundsHash = "scissor.substituted")
            },
            "load-op" to mutatedTaskList(
                base,
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store, null),
            ),
            "store-op" to mutatedTaskList(
                base,
                loadStore = GPULoadStorePlan("clear", GPUStorePlan.Discard, "opaque-black"),
            ),
            "clear-color" to mutatedTaskList(
                base,
                loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent-black"),
            ),
        )
        val nativeEvents = mutableListOf<String>()
        val textureView = fakeNative<GPUTextureView>(nativeEvents)
        val texture = fakeNative<GPUTexture>(nativeEvents) { methodName ->
            if (methodName == "createView") textureView else null
        }
        val device = fakeNative<GPUDevice>(nativeEvents) { methodName ->
            if (methodName == "createTexture") texture else null
        }
        val queue = fakeNative<GPUQueue>(nativeEvents)
        val targetSetup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            device = device,
            width = semantic.targetBounds.width,
            height = semantic.targetBounds.height,
            deviceGeneration = generation,
            targetGeneration = 1L,
            lifecycle = GPUWgpu4kPreparedSceneTargetLifecycle(),
            setupTransaction = targetSetup,
        )
        targetSetup.commit()
        val sessionCache = GPUWgpu4kColorGlyphSessionCache(device, queue)
        nativeEvents.clear()
        try {
            cases.forEach { (case, taskList) ->
                val plan = GPUFramePlanner.plan(taskList)
                val materializer = GPUWgpu4kColorGlyphFramePayloadMaterializer(
                    device,
                    queue,
                    target,
                    sessionCache,
                )
                val result = materializer.materializeReusable(
                    framePlan = plan,
                    encoderPlan = GPUCommandEncoderPlan.ordered(
                        planId = "authority.$case",
                        contextIdentity = "authority-test",
                        deviceGeneration = generation,
                        targetGeneration = 1L,
                        scopes = emptyList(),
                    ),
                    resources = GPUPreparedResourceSet(emptyList(), emptyList()),
                    generationSeal = GPUPreparedGenerationSeal(
                        deviceGeneration = generation,
                        targetGeneration = 1L,
                        resourceGenerations = emptyMap(),
                        capabilitySealHash = plan.capabilitySeal.sealHash,
                    ),
                )

                val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result, case)
                assertEquals("invalid.native-color-glyph.packet-authority", refused.code, case)
                assertEquals(emptyList(), nativeEvents, case)
                materializer.close()
            }
        } finally {
            sessionCache.close()
            target.close()
        }
    }

    private fun request(
        semantic: org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.ColorGlyph,
        readbackRequestId: GPUReadbackRequestID?,
    ) = GPUColorGlyphPreparedTaskListRequest(
        frameId = GPUFrameID(42L),
        recordingId = GPURecordingID("recording.color-glyph.builder"),
        capabilities = capabilities(),
        deviceGeneration = GPUDeviceGenerationID(9L),
        target = GPUFrameTargetRef("target.color-glyph.builder"),
        semantic = semantic,
        readbackRequestId = readbackRequestId,
    )

    private fun mutatedTaskList(
        base: GPUTaskList,
        loadStore: GPULoadStorePlan? = null,
        mutatePacket: (GPUDrawPacket) -> GPUDrawPacket = { it },
    ): GPUTaskList {
        val originalRender = base.tasks.filterIsInstance<GPUTask.Render>().single()
        val packet = mutatePacket(originalRender.drawPackets.single())
        val changedRender = GPUTask.Render(
            taskId = originalRender.taskId,
            recordingId = originalRender.recordingId,
            phase = originalRender.phase,
            target = originalRender.target,
            loadStore = loadStore ?: originalRender.loadStore,
            samplePlan = originalRender.samplePlan,
            resourceUses = originalRender.resourceUses,
            provisionalSegmentKey = originalRender.provisionalSegmentKey,
            drawPackets = listOf(packet),
            batchEligibilityByPacketId = originalRender.batchEligibilityByPacketId,
            sampleContinuationKey = originalRender.sampleContinuationKey,
            compositeMembership = originalRender.compositeMembership,
        )
        return GPUTaskList(
            frameId = base.frameId,
            capabilitySeal = base.capabilitySeal,
            recordingSeals = base.recordingSeals,
            expectedReplayKeyHash = base.expectedReplayKeyHash,
            tasks = base.tasks.map { task -> if (task === originalRender) changedRender else task },
            dependencies = base.dependencies,
            phaseOrder = base.phaseOrder,
            memoryBudget = base.memoryBudget,
            diagnostics = base.diagnostics,
        )
    }

    private fun GPUDrawPacket.withAuthority(
        renderPipelineKey: GPURenderPipelineKey? = this.renderPipelineKey,
        bindingLayoutHash: String = this.bindingLayoutHash,
        vertexSourceLabel: String = this.vertexSourceLabel,
        scissorBoundsHash: String? = this.scissorBoundsHash,
        targetStateHash: String = this.targetStateHash,
    ) = GPUDrawPacket(
        packetId = packetId,
        commandIdValue = commandIdValue,
        analysisRecordId = analysisRecordId,
        passId = passId,
        layerId = layerId,
        bindingListId = bindingListId,
        insertionReasonCode = insertionReasonCode,
        sortKey = sortKey,
        sortKeyPreimage = sortKeyPreimage,
        renderStepId = renderStepId,
        renderStepVersion = renderStepVersion,
        role = role,
        blendPlan = blendPlan,
        renderPipelineKey = renderPipelineKey,
        computePipelineKey = computePipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        uniformSlot = uniformSlot,
        resourceSlot = resourceSlot,
        semanticPayload = semanticPayload,
        vertexSourceLabel = vertexSourceLabel,
        scissorBoundsHash = scissorBoundsHash,
        targetStateHash = targetStateHash,
        originalPaintOrder = originalPaintOrder,
        resourceGeneration = resourceGeneration,
        diagnostics = diagnostics,
    )

    private inline fun <reified T> fakeNative(
        events: MutableList<String>,
        crossinline result: (String) -> Any? = { null },
    ): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        val supplied = result(method.name)
        events += method.name
        supplied ?: when (method.name) {
            "getLabel" -> "authority-test"
            "setLabel", "close" -> Unit
            "toString" -> "AuthorityTestNative(${T::class.simpleName})"
            else -> error("Unexpected authority-test native call: ${method.name}")
        }
    } as T

    private fun semantic(): org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.ColorGlyph {
        val planKey = artifactKey("550e8400-e29b-41d4-a716-446655440062", PLAN_GENERATION.toInt(), "plan")
        val atlasKey = artifactKey("550e8400-e29b-41d4-a716-446655440063", ATLAS_GENERATION.toInt(), "atlas")
        val layers = listOf(
            GPUColorGlyphLayerPayloadInput(
                planArtifactKey = planKey,
                layerGlyphID = 11u,
                paletteIndex = 0,
                atlasBounds = GPUPixelBounds(0, 0, 1, 1),
                deviceBounds = GPUPixelBounds(0, 0, 1, 1),
                premultipliedRgba = floatArrayOf(1f, 0f, 0f, 1f),
                useForeground = false,
                foregroundResolved = true,
                placementProof = placement(atlasKey, 11, GPUPixelBounds(0, 0, 1, 1)),
            ),
            GPUColorGlyphLayerPayloadInput(
                planArtifactKey = planKey,
                layerGlyphID = 12u,
                paletteIndex = 0,
                atlasBounds = GPUPixelBounds(1, 0, 2, 1),
                deviceBounds = GPUPixelBounds(0, 0, 1, 1),
                premultipliedRgba = floatArrayOf(0f, 0f, 1f, 1f),
                useForeground = false,
                foregroundResolved = true,
                placementProof = placement(atlasKey, 12, GPUPixelBounds(1, 0, 2, 1)),
            ),
        )
        val uniform = ByteBuffer.allocate(784).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(2f)
            putFloat(1f)
            putInt(2)
            putInt(0)
            putColor(1f, 0f, 0f, 1f)
            putColor(0f, 0f, 1f, 1f)
            repeat(14) { putColor(0f, 0f, 0f, 0f) }
            putRect(0f, 0f, 0.5f, 1f)
            putRect(0.5f, 0f, 0.5f, 1f)
            repeat(14) { putRect(0f, 0f, 0f, 0f) }
            putRect(0f, 0f, 1f, 1f)
            putRect(0f, 0f, 1f, 1f)
            repeat(14) { putRect(0f, 0f, 0f, 0f) }
        }.array()
        return GPUColorGlyphPayloadGatherer().gatherSemantic(
            commandIdValue = 41,
            renderStepIdentity = COLOR_GLYPH_RENDER_STEP_IDENTITY,
            planArtifactKey = planKey,
            atlasArtifactKey = atlasKey,
            atlasA8Bytes = byteArrayOf(255.toByte(), 128.toByte()),
            atlasWidth = 2,
            atlasHeight = 1,
            atlasFormat = "r8unorm",
            atlasGeneration = ATLAS_GENERATION,
            layers = layers,
            vertexData = floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f),
            indexData = intArrayOf(0, 1, 2, 0, 2, 3),
            uniformBytes = uniform,
            targetBounds = GPUPixelBounds(0, 0, 2, 1),
            scissorBounds = GPUPixelBounds(0, 0, 1, 1),
        )
    }

    private fun placement(key: GPUTextArtifactKey, glyph: Int, bounds: GPUPixelBounds) =
        GPUColorGlyphAtlasPlacementProofInput(key, glyph, 48f, 0, 0, bounds)

    private fun artifactKey(id: String, generation: Int, fingerprint: String) = GPUTextArtifactKey(
        GPUTextArtifactID(Uuid.parse(id)),
        GPUTextArtifactGeneration(generation),
        fingerprint,
    )

    private fun ByteBuffer.putColor(r: Float, g: Float, b: Float, a: Float) {
        putFloat(r); putFloat(g); putFloat(b); putFloat(a)
    }

    private fun ByteBuffer.putRect(x: Float, y: Float, w: Float, h: Float) {
        putFloat(x); putFloat(y); putFloat(w); putFloat(h)
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "color-glyph-builder")),
        snapshotId = "capabilities-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm, GPUTextureFormat.R8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )

    private companion object {
        const val PLAN_GENERATION = 7L
        const val ATLAS_GENERATION = 2L
    }
}
