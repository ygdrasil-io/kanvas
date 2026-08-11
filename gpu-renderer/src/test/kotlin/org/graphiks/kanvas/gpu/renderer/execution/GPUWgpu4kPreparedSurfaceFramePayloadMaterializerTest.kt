package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotConsumerRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.fromBatchPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor

class GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest {
    @Test
    fun `vertices frame materializes with exact vertex uploads and no submission side effect`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = verticesCapturedPreparedSurfaceInputs(),
        )
        try {
            val eventsBefore = fixture.native.events.toList()

            val materializeResult = fixture.materialize()
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                materializeResult,
                (materializeResult as? GPUPreparedNativeFramePayloadMaterialization.Refused)
                    ?.code.toString(),
            )

            assertEquals(
                eventsBefore.count { it.startsWith("encoder.finish") },
                fixture.native.events.count { it.startsWith("encoder.finish") },
            )
            assertEquals(
                eventsBefore.count { it.startsWith("queue.submit") },
                fixture.native.events.count { it.startsWith("queue.submit") },
            )
            assertTrue(
                materialized.draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                    .flatMap { it.commands }
                    .any { command ->
                        command is GPUPreparedNativeRenderCommand.Draw ||
                            command is GPUPreparedNativeRenderCommand.DrawIndexed
                    },
                "vertices materialization must close with one exact typed draw",
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `vertices render operands stay open through submit and close once on completion`() {
        val flow = verticesPayloadFlowFixture()
        val backend = GPUWgpu4kFrameEncodingBackend(
            deviceGeneration = flow.generationSeal.deviceGeneration,
            device = flow.native.device,
            queue = flow.native.queue,
            canonicalSceneTargetView = flow.target.view,
        )
        val registry = GPURuntimeResourceAdapter()
        val encodingWitness = GPUFrameCoreTestFixture.preparedFrame()
        var ownership: GPUPreparedNativeFrameOwnership? = null
        try {
            val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
                GPUWgpu4kPreparedVerticesRenderRunMaterializer(flow.native.device)
                    .materializeAcceptedRun(
                        flow.plan,
                        flow.generationSeal.deviceGeneration,
                        flow.targetViewOperand,
                    ),
            )
            val owner = ready.ownedResources.single()
                as GPUPreparedRenderRunOwnedResources
            ready.uniformUploads.forEach { upload ->
                encodePreparedImageUniformUpload(flow.native.queue, upload)
            }
            assertTrue(
                flow.native.writeBufferCalls.any { call ->
                    call.bufferLabel.startsWith("Kanvas.frame.preparedVertices.")
                },
                "vertices buffers must be written before scope encoding",
            )
            val visibleHandles = mutableListOf<AutoCloseable>()
            val borrowedRender = assertIs<GPUPreparedNativeScopeOperand.Render>(
                ready.scopeOperands.single(),
            ).toTargetBoundVerticesRender(flow.generationSeal, visibleHandles)
            assertTrue(
                borrowedRender.commands
                    .filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                    .all { group -> group.bindGroup.ownership == GPUPreparedNativeOperandOwnership.Borrowed },
            )
            assertTrue(
                borrowedRender.commands
                    .filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
                    .all { command -> command.buffer.ownership == GPUPreparedNativeOperandOwnership.Borrowed },
            )
            val distinctVisible = visibleHandles.distinctBy(System::identityHashCode)
            owner.detachOwnedHandles(distinctVisible)
            val anchor = GPUPreparedNativeCompletionAnchor(distinctVisible)
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = flow.frameId,
                    contextIdentity = flow.contextIdentity,
                    encoderPlanId = "frame.${flow.frameId.value}",
                    deviceGeneration = flow.generationSeal.deviceGeneration,
                    targetGeneration = flow.generationSeal.targetGeneration,
                    scopes = listOf(flow.plan.exactScopeKey),
                ),
                scopeOperands = listOf(borrowedRender),
                scopeOperandKeys = listOf(flow.plan.exactScopeKey.operandKeys),
                auxiliaryOwnedHandles = listOf(
                    GPUPreparedNativeAuxiliaryHandle(
                        anchor,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                    GPUPreparedNativeAuxiliaryHandle(
                        owner,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                ),
            )
            val frameHandles = (
                distinctVisible + owner.ownedHandlesSnapshot()
                ).distinctBy(System::identityHashCode)
            assertTrue(
                frameHandles.all { flow.native.closeCounts[it] == null },
                "vertices frame handles must stay open through materialization",
            )
            val draft = GPUPreparedNativeFrameDraft(payload)
            ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                registry.registerPreparedNativeFrameDraft(draft),
            ).ownership
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                ownership.bindLateSurface(
                    acquiredSurface = null,
                    binding = GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
                ownership.consume(payload.identity),
            )
            val encoder = backend.createCommandEncoder("vertices-close-once")
            flow.encoderPlan.scopes.zip(payload.scopeOperands).forEach { (scope, operand) ->
                encoder.encode(
                    scope,
                    encodingWitness,
                    GPUFrameCoreTestFixture.sceneTarget(
                        targetGeneration = flow.generationSeal.targetGeneration,
                    ),
                    operand,
                )
            }
            backend.submit(encoder.finish())
            assertEquals(1, flow.native.events.count { it == "encoder.finish" })
            assertEquals(1, flow.native.events.count { it == "queue.submit" })
            assertTrue(frameHandles.all { flow.native.closeCounts[it] == null })

            assertTrue(ownership.markSubmitted())
            assertTrue(ownership.releaseAfterCompletion())
            assertTrue(frameHandles.all { flow.native.closeCounts[it] == 1 })
            assertFalse(ownership.releaseAfterCompletion())
        } finally {
            ownership?.rollback()
            if (encodingWitness.claimForRollback()) encodingWitness.rollback.execute()
            runCatching { registry.close() }
            runCatching { backend.close() }
            flow.close()
        }
    }

    @Test
    fun `equivalent ColorGlyph plan instances canonicalize to one native buffer triplet`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(
                colorGlyphCommandIds = setOf(0, 1),
                coalescedColorGlyphScope = true,
            ).withSecondColorGlyphPlanCopy(),
        )
        try {
            val result = fixture.materialize()
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                result,
                result.toString(),
            )

            val colorWrites = fixture.native.writeBufferCalls.filter { call ->
                call.bufferLabel.startsWith("Kanvas.frame.colorGlyph.")
            }
            assertEquals(
                listOf(
                    "Kanvas.frame.colorGlyph.vertices",
                    "Kanvas.frame.colorGlyph.indices",
                    "Kanvas.frame.colorGlyph.uniforms",
                ),
                colorWrites.map { call -> call.bufferLabel },
            )
            assertTrue(materialized.draft.disposeBeforeRegistration())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `conflicting ColorGlyph plans refuse before native side effects`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(
                colorGlyphCommandIds = setOf(0, 1),
                coalescedColorGlyphScope = true,
            ).withSecondColorGlyphPlanCopy { plan ->
                val bytes = plan.uniformBytesForUpload()
                bytes[plan.slices.first().uniformSizeBytes.toInt()] =
                    (bytes[plan.slices.first().uniformSizeBytes.toInt()].toInt() xor 0x01).toByte()
                plan.rebuiltForMaterializerCanonicalityTest(bytes)
            },
        )
        try {
            val eventsBefore = fixture.native.events.toList()
            val writesBefore = fixture.native.writeBufferCalls.size

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )

            assertEquals(GPUPreparedTextPreflightRefusalCodes.OPERAND, refused.code)
            assertEquals(eventsBefore, fixture.native.events)
            assertEquals(writesBefore, fixture.native.writeBufferCalls.size)
            assertEquals(0, fixture.native.events.count { it == "encoder.finish" })
            assertEquals(0, fixture.native.events.count { it == "queue.submit" })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `two ColorGlyph packets in one accepted scope keep native draw order`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(
                colorGlyphCommandIds = setOf(0, 1),
                coalescedColorGlyphScope = true,
            ),
        )
        try {
            val result = fixture.materialize()
            val materialized = assertIs<
                GPUPreparedNativeFramePayloadMaterialization.Materialized
                >(result, result.toString())
            val render = materialized.draft.payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                .single()

            assertEquals(listOf(0, 1), render.semanticPayloads.map {
                it.payloadRef.commandIdValue
            })
            assertEquals(
                2,
                render.commands
                    .filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>()
                    .size,
            )
            val colorWrites = fixture.native.writeBufferCalls.filter { call ->
                call.bufferLabel.startsWith("Kanvas.frame.colorGlyph.")
            }
            assertEquals(
                listOf(
                    "Kanvas.frame.colorGlyph.vertices",
                    "Kanvas.frame.colorGlyph.indices",
                    "Kanvas.frame.colorGlyph.uniforms",
                ),
                colorWrites.map { call -> call.bufferLabel },
            )
            val semantics = render.semanticPayloads
                .map { semantic -> assertIs<GPUDrawSemanticPayload.ColorGlyph>(semantic) }
            val uniformSnapshot = colorWrites.single {
                it.bufferLabel == "Kanvas.frame.colorGlyph.uniforms"
            }.snapshot
            val firstUniform = semantics[0].uniformBytes.map(Int::toByte).toByteArray()
            val secondUniform = semantics[1].uniformBytes.map(Int::toByte).toByteArray()
            assertContentEquals(firstUniform, uniformSnapshot.copyOfRange(0, firstUniform.size))
            assertContentEquals(
                secondUniform,
                uniformSnapshot.copyOfRange(1024, 1024 + secondUniform.size),
            )
            val nativeUniforms = ByteBuffer.wrap(uniformSnapshot).order(ByteOrder.LITTLE_ENDIAN)
            listOf(0, 1024).forEach { base ->
                assertEquals(16f, nativeUniforms.getFloat(base))
                assertEquals(16f, nativeUniforms.getFloat(base + 4))
                assertEquals(1, nativeUniforms.getInt(base + 8))
                assertEquals(0.5f, nativeUniforms.getFloat(base + 16))
                assertEquals(0f, nativeUniforms.getFloat(base + 20))
                assertEquals(0f, nativeUniforms.getFloat(base + 24))
                assertEquals(0.5f, nativeUniforms.getFloat(base + 28))
                assertEquals(0f, nativeUniforms.getFloat(base + 272))
                assertEquals(0f, nativeUniforms.getFloat(base + 276))
                assertEquals(0.25f, nativeUniforms.getFloat(base + 280))
                assertEquals(0.25f, nativeUniforms.getFloat(base + 284))
                assertEquals(1f, nativeUniforms.getFloat(base + 528))
                assertEquals(1f, nativeUniforms.getFloat(base + 532))
                assertEquals(4f, nativeUniforms.getFloat(base + 536))
                assertEquals(4f, nativeUniforms.getFloat(base + 540))
            }
            assertTrue(materialized.draft.disposeBeforeRegistration())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `ColorGlyph bind group failure rolls back every already created frame buffer once`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(
                colorGlyphCommandIds = setOf(0, 1),
                coalescedColorGlyphScope = true,
            ),
        )
        try {
            fixture.native.fail("createBindGroup", ordinal = 1)

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )

            assertEquals("failed.color_glyph.materialization", refused.code)
            val colorBuffers = fixture.native.writeBufferCalls
                .filter { call -> call.bufferLabel.startsWith("Kanvas.frame.colorGlyph.") }
                .mapNotNull { call -> call.buffer }
                .distinctBy(System::identityHashCode)
            assertEquals(3, colorBuffers.size)
            assertTrue(colorBuffers.all { buffer ->
                fixture.native.closeCounts[buffer] == 1
            })
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            assertEquals(null, refused.retainedCloseOwner)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `mixed TextA8 and ColorGlyph upload resolved currentColor premul alpha bytes`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(
                colorGlyphCommandIds = setOf(1),
                colorGlyphPremultipliedRgba =
                    floatArrayOf(0.125f, 0.25f, 0.375f, 0.5f),
                colorGlyphUseForeground = true,
            ),
        )
        try {
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                fixture.materialize(),
            )
            val colorSemantic = materialized.draft.payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                .flatMap { render -> render.semanticPayloads }
                .filterIsInstance<GPUDrawSemanticPayload.ColorGlyph>()
                .single()
            assertTrue(colorSemantic.layers.single().useForeground)
            assertTrue(colorSemantic.layers.single().foregroundResolved)

            val uploaded = fixture.native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.frame.colorGlyph.uniforms"
            }.snapshot
            val uniform = ByteBuffer.wrap(uploaded).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0.125f, uniform.getFloat(16))
            assertEquals(0.25f, uniform.getFloat(20))
            assertEquals(0.375f, uniform.getFloat(24))
            assertEquals(0.5f, uniform.getFloat(28))
            assertTrue(materialized.draft.disposeBeforeRegistration())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `prepared text materializer attaches exact uploads and instanced runs to one payload`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(),
        )
        try {
            val result = fixture.materialize()
            val materialized = assertIs<
                GPUPreparedNativeFramePayloadMaterialization.Materialized
                >(result, result.toString())
            val payload = materialized.draft.payload

            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.sourceStepIndex },
                payload.scopeOperands.map { it.sourceStepIndex },
            )
            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.nativeOperandKeys },
                payload.scopeOperandKeys,
            )
            assertEquals(
                listOf(
                    "Kanvas.frame.preparedText.instances",
                    "Kanvas.frame.preparedText.draw-uniforms",
                    "Kanvas.frame.preparedText.material-uniforms",
                ),
                fixture.native.writeBufferCalls.map { call -> call.bufferLabel },
            )
            val renders = payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            assertEquals(listOf(64, 36), renders.map { render ->
                render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.Draw>()
                    .single().drawCall.instanceCount
            })
            assertTrue(renders.all { render ->
                render.commands.filterIsInstance<
                    GPUPreparedNativeRenderCommand.SetBindGroup
                    >().map { it.index } == listOf(0, 1, 2)
            })
            assertTrue(materialized.draft.disposeBeforeRegistration())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `ColorGlyph frame resources stay open through submit and close once on completion`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(
                colorGlyphCommandIds = setOf(0, 1),
                coalescedColorGlyphScope = true,
            ),
        )
        val backend = GPUWgpu4kFrameEncodingBackend(
            deviceGeneration = fixture.input.generationSeal.deviceGeneration,
            device = fixture.native.device,
            queue = fixture.native.queue,
            canonicalSceneTargetView = fixture.target.view,
        )
        val registry = GPURuntimeResourceAdapter()
        val encodingWitness = GPUFrameCoreTestFixture.preparedFrame()
        var ownership: GPUPreparedNativeFrameOwnership? = null
        try {
            val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                fixture.materialize(),
            ).draft
            val payload = draft.payload
            val frameHandles = buildList<AutoCloseable> {
                payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.TextureUpload>()
                    .forEach { add(it.destination.texture) }
                payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                    .flatMap { it.commands }
                    .forEach { command ->
                        when (command) {
                            is GPUPreparedNativeRenderCommand.SetBindGroup ->
                                add(command.bindGroup.bindGroup)
                            is GPUPreparedNativeRenderCommand.SetVertexBuffer ->
                                add(command.buffer.buffer)
                            is GPUPreparedNativeRenderCommand.SetIndexBuffer ->
                                add(command.buffer.buffer)
                            else -> Unit
                        }
                    }
                fixture.native.writeBufferCalls.mapNotNullTo(this) { it.buffer }
            }.distinctBy(System::identityHashCode)
            assertTrue(frameHandles.all { fixture.native.closeCounts[it] == null })

            ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                registry.registerPreparedNativeFrameDraft(draft),
            ).ownership
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                ownership.bindLateSurface(
                    acquiredSurface = null,
                    binding = GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
                ownership.consume(payload.identity),
            )
            val encoder = backend.createCommandEncoder("color-glyph-completion")
            fixture.input.encoderPlan.scopes.zip(payload.scopeOperands)
                .forEach { (scope, operand) ->
                    encoder.encode(
                        scope,
                        encodingWitness,
                        GPUFrameCoreTestFixture.sceneTarget(
                            targetGeneration =
                                fixture.input.generationSeal.targetGeneration,
                        ),
                        operand,
                    )
                }
            backend.submit(encoder.finish())
            assertEquals(1, fixture.native.writeTextureCalls)
            assertEquals(1, fixture.native.events.count { it == "encoder.finish" })
            assertEquals(1, fixture.native.events.count { it == "queue.submit" })
            assertTrue(frameHandles.all { fixture.native.closeCounts[it] == null })

            assertTrue(ownership.markSubmitted())
            assertTrue(ownership.releaseAfterCompletion())
            assertTrue(frameHandles.all { fixture.native.closeCounts[it] == 1 })
            assertTrue(ownership.claimOutputMapping())
            assertTrue(ownership.releaseOutputAfterReadback())
            val closed = fixture.native.closeCounts.toMap()
            assertFalse(ownership.releaseAfterCompletion())
            assertFalse(ownership.releaseOutputAfterReadback())
            assertEquals(closed, fixture.native.closeCounts)
        } finally {
            ownership?.rollback()
            if (encodingWitness.claimForRollback()) encodingWitness.rollback.execute()
            runCatching { registry.close() }
            runCatching { backend.close() }
            fixture.close()
        }
    }

    @Test
    fun `destination snapshot rollback retains only failed view and never recloses texture`() {
        var injected = 0
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = destinationReadPreparedTextInputs(),
            onDestinationSnapshotViewCreated = {
                injected += 1
                error("injected after destination snapshot view creation")
            },
        )
        try {
            fixture.native.failCloseOnce(DESTINATION_SNAPSHOT_VIEW_LABEL)

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )

            assertEquals(1, injected)
            val texture = fixture.native.createdHandles(DESTINATION_SNAPSHOT_TEXTURE_LABEL)
                .single()
            val view = fixture.native.createdHandles(DESTINATION_SNAPSHOT_VIEW_LABEL)
                .single()
            assertEquals(1, fixture.native.closeAttempts(DESTINATION_SNAPSHOT_VIEW_LABEL))
            assertEquals(1, fixture.native.closeAttempts(DESTINATION_SNAPSHOT_TEXTURE_LABEL))
            assertEquals(1, fixture.native.closeCounts[view])
            assertEquals(1, fixture.native.closeCounts[texture])
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedCloseOwner)

            val ledger = requireNotNull(refused.retainedPreRegistrationLedger)
            assertEquals(listOf(view), ledger.pendingHandlesSnapshot())
            assertTrue(ledger.closeRetainingFailures())
            assertEquals(2, fixture.native.closeAttempts(DESTINATION_SNAPSHOT_VIEW_LABEL))
            assertEquals(1, fixture.native.closeAttempts(DESTINATION_SNAPSHOT_TEXTURE_LABEL))
            assertEquals(2, fixture.native.closeCounts[view])
            assertEquals(1, fixture.native.closeCounts[texture])
            assertEquals(
                listOf(
                    "close:$DESTINATION_SNAPSHOT_VIEW_LABEL",
                    "close:$DESTINATION_SNAPSHOT_TEXTURE_LABEL",
                    "close:$DESTINATION_SNAPSHOT_VIEW_LABEL",
                ),
                fixture.native.events.filter { event ->
                    event == "close:$DESTINATION_SNAPSHOT_VIEW_LABEL" ||
                        event == "close:$DESTINATION_SNAPSHOT_TEXTURE_LABEL"
                },
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `destination snapshot completion retries failed view after bind group and texture once`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = destinationReadPreparedTextInputs(),
        )
        val backend = GPUWgpu4kFrameEncodingBackend(
            deviceGeneration = fixture.input.generationSeal.deviceGeneration,
            device = fixture.native.device,
            queue = fixture.native.queue,
            canonicalSceneTargetView = fixture.target.view,
        )
        val registry = GPURuntimeResourceAdapter()
        val witness = GPUFrameCoreTestFixture.preparedFrame()
        var ownership: GPUPreparedNativeFrameOwnership? = null
        try {
            val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                fixture.materialize(),
            ).draft
            val payload = draft.payload
            val snapshotTexture = payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Copy>()
                .single()
                .destination.texture
            val snapshotView = fixture.native.createdHandles(DESTINATION_SNAPSHOT_VIEW_LABEL)
                .single()
            val bindGroup = fixture.native.createdHandles("Kanvas.frame.colorGlyph.bindGroup0")
                .single()
            assertEquals(
                fixture.native.createdHandles(DESTINATION_SNAPSHOT_TEXTURE_LABEL).single(),
                snapshotTexture,
            )
            assertTrue(listOf(bindGroup, snapshotView, snapshotTexture).all { handle ->
                fixture.native.closeCounts[handle] == null
            })

            ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                registry.registerPreparedNativeFrameDraft(draft),
            ).ownership
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                ownership.bindLateSurface(
                    null,
                    GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
                ownership.consume(payload.identity),
            )
            val encoder = backend.createCommandEncoder("color-glyph-destination-completion")
            fixture.input.encoderPlan.scopes.zip(payload.scopeOperands)
                .forEach { (scope, operand) ->
                    encoder.encode(
                        scope,
                        witness,
                        GPUFrameCoreTestFixture.sceneTarget(
                            targetGeneration =
                                fixture.input.generationSeal.targetGeneration,
                        ),
                        operand,
                    )
                }
            backend.submit(encoder.finish())
            assertTrue(listOf(bindGroup, snapshotView, snapshotTexture).all { handle ->
                fixture.native.closeCounts[handle] == null
            })
            assertTrue(ownership.markSubmitted())
            fixture.native.failCloseOnce(DESTINATION_SNAPSHOT_VIEW_LABEL)
            val closeEventStart = fixture.native.events.size

            assertFalse(ownership.releaseAfterCompletion())
            val completionCloses = fixture.native.events.drop(closeEventStart)
            val bindGroupClose = completionCloses.indexOf(
                "close:Kanvas.frame.colorGlyph.bindGroup0",
            )
            val viewClose = completionCloses.indexOf(
                "close:$DESTINATION_SNAPSHOT_VIEW_LABEL",
            )
            val textureClose = completionCloses.indexOf(
                "close:$DESTINATION_SNAPSHOT_TEXTURE_LABEL",
            )
            assertTrue(bindGroupClose >= 0 && bindGroupClose < viewClose)
            assertTrue(viewClose < textureClose)
            assertEquals(1, fixture.native.closeCounts[bindGroup])
            assertEquals(1, fixture.native.closeCounts[snapshotView])
            assertEquals(1, fixture.native.closeCounts[snapshotTexture])

            registry.close()
            assertEquals(1, fixture.native.closeAttempts("Kanvas.frame.colorGlyph.bindGroup0"))
            assertEquals(2, fixture.native.closeAttempts(DESTINATION_SNAPSHOT_VIEW_LABEL))
            assertEquals(1, fixture.native.closeAttempts(DESTINATION_SNAPSHOT_TEXTURE_LABEL))
            assertEquals(1, fixture.native.closeCounts[bindGroup])
            assertEquals(2, fixture.native.closeCounts[snapshotView])
            assertEquals(1, fixture.native.closeCounts[snapshotTexture])
            assertFalse(ownership.releaseAfterCompletion())
        } finally {
            ownership?.rollback()
            if (witness.claimForRollback()) witness.rollback.execute()
            runCatching { registry.close() }
            runCatching { backend.close() }
            fixture.close()
        }
    }

    @Test
    fun `post-build destination authority mutation matrix refuses before every side effect`() {
        val analytic = destinationReadPreparedTextInputs()
        val coverageMask = destinationReadPreparedTextInputs(coverageMask = true)
        val cases = listOf(
            DestinationAuthorityMutation("consumer.groupingCommandId") { input ->
                input.withDestinationCopyMutation { copy ->
                    copy.rebuiltForDestinationMutation(
                        consumers = copy.consumers.map { consumer ->
                            consumer.copy(groupingCommandId = "forged-group")
                        },
                    )
                }
            },
            DestinationAuthorityMutation("consumer.renderTaskId+dependency") { input ->
                input.withCoherentDestinationRenderTaskMutation()
            },
            DestinationAuthorityMutation("consumer.packetId") { input ->
                input.withDestinationCopyMutation { copy ->
                    copy.rebuiltForDestinationMutation(
                        consumers = copy.consumers.map { consumer ->
                            consumer.copy(
                                packetId = GPUDrawPacketID("${consumer.packetId.value}.forged"),
                            )
                        },
                    )
                }
            },
            DestinationAuthorityMutation("consumer.commandId") { input ->
                input.withDestinationCopyMutation { copy ->
                    copy.rebuiltForDestinationMutation(
                        consumers = copy.consumers.map { consumer ->
                            consumer.copy(
                                commandId = GPUDrawCommandID(consumer.commandId.value + 1),
                            )
                        },
                    )
                }
            },
            DestinationAuthorityMutation("generation.seal") { input ->
                input.copy(
                    generationSeal = GPUPreparedGenerationSeal(
                        deviceGeneration = input.generationSeal.deviceGeneration,
                        targetGeneration = input.generationSeal.targetGeneration + 1L,
                        resourceGenerations = input.generationSeal.resourceGenerations,
                        capabilitySealHash = input.generationSeal.capabilitySealHash,
                    ),
                )
            },
            DestinationAuthorityMutation("snapshot.ref") { input ->
                input.withDestinationCopyMutation { copy ->
                    copy.rebuiltForDestinationMutation(
                        snapshot = GPUFrameTextureRef("${copy.snapshot.value}.forged"),
                    )
                }
            },
            DestinationAuthorityMutation("snapshot.preparation") { input ->
                input.withPreparationMutation(GPUFrameResourceRole.DestinationSnapshot) {
                    request ->
                    request.rebuiltForDestinationMutation(byteSize = request.byteSize + 4L)
                }
            },
            DestinationAuthorityMutation("snapshot.evidence") { input ->
                input.copy(
                    resources = input.resources.rebuiltForDestinationMutation(
                        ordinaryResources = input.resources.ordinaryResources.map { evidence ->
                            if (evidence.role == GPUFrameResourceRole.DestinationSnapshot) {
                                evidence.copy(role = GPUFrameResourceRole.ClipMask)
                            } else {
                                evidence
                            }
                        },
                    ),
                )
            },
            DestinationAuthorityMutation("snapshot.evidence-generation") { input ->
                input.copy(
                    resources = input.resources.rebuiltForDestinationMutation(
                        ordinaryResources = input.resources.ordinaryResources.map { evidence ->
                            if (evidence.role == GPUFrameResourceRole.DestinationSnapshot) {
                                evidence.copy(
                                    resourceGeneration = evidence.resourceGeneration + 1L,
                                )
                            } else {
                                evidence
                            }
                        },
                    ),
                )
            },
            DestinationAuthorityMutation("formula") { input ->
                input.withDestinationPacketMutation { packet ->
                    packet.rebuiltForDestinationMutation(
                        blendPlan = GPUBlendPlan.ShaderBlendWithDstRead(
                            mode = GPUBlendMode.COLOR_DODGE,
                            formulaId = "color_dodge@forged",
                            sourceCoverageEncoding =
                                GPUSourceCoverageEncoding.ScalarCoverageInShader,
                        ),
                    )
                }
            },
            DestinationAuthorityMutation("program.pipelineSeal") { input ->
                input.withDestinationPacketMutation { packet ->
                    packet.rebuiltForDestinationMutation(
                        renderPipelineKey = GPURenderPipelineKey(
                            "${requireNotNull(packet.renderPipelineKey).value}.forged",
                        ),
                    )
                }
            },
            DestinationAuthorityMutation("binding.layoutSeal") { input ->
                input.withDestinationPacketMutation { packet ->
                    packet.rebuiltForDestinationMutation(
                        bindingLayoutHash = "${packet.bindingLayoutHash}.forged",
                    )
                }
            },
            DestinationAuthorityMutation("binding.preflightSeal") { input ->
                input.withDestinationBindingMutation { binding ->
                    binding.rebuiltForDestinationMutation(
                        preflightSeal = binding.preflightSeal
                            .rebuiltForDestinationMutation(
                                blendPlanIdentity =
                                    "${binding.preflightSeal.blendPlanIdentity}.forged",
                            ),
                    )
                }
            },
            DestinationAuthorityMutation("binding.missing") { input ->
                input.withMissingDestinationBinding()
            },
            DestinationAuthorityMutation("clip.executionIdentity") { input ->
                input.withDestinationPacketMutation { packet ->
                    val clip = packet.clipExecutionPlan as
                        GPUClipExecutionPlan.AnalyticCoverage
                    packet.rebuiltForDestinationMutation(
                        clipExecutionPlan = GPUClipExecutionPlan.AnalyticCoverage(
                            geometry = clip.geometry,
                            scissor = clip.scissor,
                            antiAlias = !clip.antiAlias,
                        ),
                    )
                }
            },
            DestinationAuthorityMutation("clip.semanticIdentity") { input ->
                input.withDestinationBindingMutation { binding ->
                    val clip = binding.preflightSeal.colorGlyphClip as
                        org.graphiks.kanvas.gpu.renderer.recording
                            .GPUPreparedColorGlyphClipPreflightSeal.NonMask
                    val forged = "${clip.semanticIdentity}.forged"
                    binding.rebuiltForDestinationMutation(
                        preflightSeal = binding.preflightSeal
                            .rebuiltForDestinationMutation(
                                clipIdentity = forged,
                                colorGlyphClip = clip.copy(semanticIdentity = forged),
                            ),
                    )
                }
            },
            DestinationAuthorityMutation("clip.analyticRectFacts.left") { input ->
                input.withDestinationBindingMutation { binding ->
                    val clip = binding.preflightSeal.colorGlyphClip as
                        org.graphiks.kanvas.gpu.renderer.recording
                            .GPUPreparedColorGlyphClipPreflightSeal.NonMask
                    val facts = requireNotNull(clip.analyticRect)
                    binding.rebuiltForDestinationMutation(
                        preflightSeal = binding.preflightSeal
                            .rebuiltForDestinationMutation(
                                colorGlyphClip = clip.copy(
                                    analyticRect = facts.copy(
                                        left = facts.left + 1f,
                                    ),
                                ),
                            ),
                    )
                }
            },
            DestinationAuthorityMutation("clip.analyticRectFacts.antiAlias") { input ->
                input.withDestinationBindingMutation { binding ->
                    val clip = binding.preflightSeal.colorGlyphClip as
                        org.graphiks.kanvas.gpu.renderer.recording
                            .GPUPreparedColorGlyphClipPreflightSeal.NonMask
                    val facts = requireNotNull(clip.analyticRect)
                    binding.rebuiltForDestinationMutation(
                        preflightSeal = binding.preflightSeal
                            .rebuiltForDestinationMutation(
                                colorGlyphClip = clip.copy(
                                    analyticRect = facts.copy(
                                        antiAlias = !facts.antiAlias,
                                    ),
                                ),
                            ),
                    )
                }
            },
            DestinationAuthorityMutation("mask.producer-resource", coverageMask = true) { input ->
                input.withCoverageMaskProducerMutation { producer ->
                    producer.rebuiltForDestinationMutation(
                        target = GPUFrameTargetRef("${producer.target.value}.forged"),
                    )
                }
            },
            DestinationAuthorityMutation("mask.orderingToken", coverageMask = true) { input ->
                input.withDestinationBindingMutation { binding ->
                    val clip = binding.preflightSeal.colorGlyphClip as
                        org.graphiks.kanvas.gpu.renderer.recording
                            .GPUPreparedColorGlyphClipPreflightSeal.CoverageMask
                    binding.rebuiltForDestinationMutation(
                        preflightSeal = binding.preflightSeal
                            .rebuiltForDestinationMutation(
                                colorGlyphClip = clip.copy(
                                    orderingToken = "${clip.orderingToken}.forged",
                                ),
                            ),
                    )
                }
            },
            DestinationAuthorityMutation("mask.preparation", coverageMask = true) { input ->
                input.withPreparationMutation(GPUFrameResourceRole.ClipMask) { request ->
                    request.rebuiltForDestinationMutation(
                        role = GPUFrameResourceRole.DestinationSnapshot,
                    )
                }
            },
            DestinationAuthorityMutation(
                "mask.evidence-generation",
                coverageMask = true,
            ) { input ->
                input.copy(
                    resources = input.resources.rebuiltForDestinationMutation(
                        ordinaryResources = input.resources.ordinaryResources.map { evidence ->
                            if (evidence.role == GPUFrameResourceRole.ClipMask) {
                                evidence.copy(
                                    resourceGeneration = evidence.resourceGeneration + 1L,
                                )
                            } else {
                                evidence
                            }
                        },
                    ),
                )
            },
            DestinationAuthorityMutation("mask.producer-order", coverageMask = true) { input ->
                input.withCoverageMaskProducerAfterDestinationCopy()
            },
            DestinationAuthorityMutation("mask.operand-order", coverageMask = true) { input ->
                input.withCoverageMaskOperandOrderMutation()
            },
        )

        cases.forEach { case ->
            var cacheAcquires = 0
            var targetBorrows = 0
            val source = if (case.coverageMask) coverageMask else analytic
            val mutated = case.mutate(source)
            if (case.name.startsWith("clip.analyticRectFacts.") ||
                case.name == "mask.orderingToken"
            ) {
                assertNotEquals(source.framePlan.stableHash(), mutated.framePlan.stableHash(), case.name)
                assertNotEquals(source.framePlan.dumpLines(), mutated.framePlan.dumpLines(), case.name)
            }
            val fixture = fixture(
                shape = PreparedSurfaceFixtureShape.ImageOnly,
                inputOverride = mutated,
                onCacheAcquire = { cacheAcquires += 1 },
                onTargetBorrow = { targetBorrows += 1 },
            )
            try {
                val nativeEventCount = fixture.native.events.size
                val writeBufferCount = fixture.native.writeBufferCalls.size
                val imageHandleCount = fixture.imageFactory.handleCreates
                val lifecycle = fixture.targetLifecycle.snapshot()

                val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                    fixture.materialize(),
                    case.name,
                )

                assertEquals(0, cacheAcquires, case.name)
                assertEquals(0, targetBorrows, case.name)
                assertEquals(nativeEventCount, fixture.native.events.size, case.name)
                assertEquals(writeBufferCount, fixture.native.writeBufferCalls.size, case.name)
                assertEquals(imageHandleCount, fixture.imageFactory.handleCreates, case.name)
                assertEquals(lifecycle, fixture.targetLifecycle.snapshot(), case.name)
                assertEquals(null, refused.retainedDraft, case.name)
                assertEquals(null, refused.retainedPreRegistrationLedger, case.name)
                assertEquals(null, refused.retainedCloseOwner, case.name)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `core image text frame preserves full scope order in one submit and readback`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.CoreImageText)
        val backend = GPUWgpu4kFrameEncodingBackend(
            deviceGeneration = fixture.input.generationSeal.deviceGeneration,
            device = fixture.native.device,
            queue = fixture.native.queue,
            canonicalSceneTargetView = fixture.target.view,
        )
        val registry = GPURuntimeResourceAdapter()
        val witness = GPUFrameCoreTestFixture.preparedFrame()
        var ownership: GPUPreparedNativeFrameOwnership? = null
        try {
            val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                fixture.materialize(),
            ).draft
            val payload = draft.payload
            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.sourceStepIndex },
                payload.scopeOperands.map { it.sourceStepIndex },
            )
            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.nativeOperandKeys },
                payload.scopeOperandKeys,
            )
            assertEquals(
                listOf("CorePrimitive", "SampledImage", "TextA8"),
                payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                    .map { render -> render.semanticPayloads.single().canonicalType },
            )

            ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                registry.registerPreparedNativeFrameDraft(draft),
            ).ownership
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                ownership.bindLateSurface(
                    null,
                    GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
                ownership.consume(payload.identity),
            )
            val encoder = backend.createCommandEncoder("prepared-core-image-text")
            fixture.input.encoderPlan.scopes.zip(payload.scopeOperands).forEach { (scope, operand) ->
                encoder.encode(
                    scope,
                    witness,
                    GPUFrameCoreTestFixture.sceneTarget(
                        targetGeneration = fixture.input.generationSeal.targetGeneration,
                    ),
                    operand,
                )
            }
            backend.submit(encoder.finish())

            assertEquals(listOf("core", "image", "text"), fixture.native.renderPipelineKinds)
            assertEquals(2, fixture.native.writeTextureCalls)
            assertEquals(1, fixture.native.events.count { it == "encoder.finish" })
            assertEquals(1, fixture.native.events.count { it == "queue.submit" })
            assertEquals(1, fixture.native.readbackCopyCalls)
            assertTrue(ownership.markSubmitted())
            assertTrue(ownership.releaseAfterCompletion())
            assertTrue(ownership.claimOutputMapping())
            assertTrue(ownership.releaseOutputAfterReadback())
        } finally {
            ownership?.rollback()
            if (witness.claimForRollback()) witness.rollback.execute()
            runCatching { registry.close() }
            runCatching { backend.close() }
            fixture.close()
        }
    }

    @Test
    fun `prepared text completion close failure is quarantined and retried once`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(),
        )
        val registry = GPURuntimeResourceAdapter()
        var ownership: GPUPreparedNativeFrameOwnership? = null
        try {
            val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                fixture.materialize(),
            ).draft
            val payload = draft.payload
            val atlas = assertIs<GPUPreparedNativeScopeOperand.TextureUpload>(
                payload.scopeOperands.first {
                    it is GPUPreparedNativeScopeOperand.TextureUpload &&
                        it.uploadRole == "prepared-text-r8"
                },
            ).destination.texture
            assertEquals(
                listOf(atlas),
                fixture.native.createdHandles("Kanvas.frame.preparedText.r8-page"),
            )
            ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                registry.registerPreparedNativeFrameDraft(draft),
            ).ownership
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                ownership.bindLateSurface(
                    null,
                    GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
                ownership.consume(payload.identity),
            )
            assertTrue(ownership.markSubmitted())
            fixture.native.failCloseOnce("Kanvas.frame.preparedText.r8-page")

            assertFalse(ownership.releaseAfterCompletion())
            assertEquals(
                1,
                fixture.native.closeAttempts("Kanvas.frame.preparedText.r8-page"),
            )
            registry.close()
            assertEquals(
                2,
                fixture.native.closeAttempts("Kanvas.frame.preparedText.r8-page"),
            )
            assertEquals(2, fixture.native.closeCounts[atlas])
        } finally {
            ownership?.rollback()
            runCatching { registry.close() }
            fixture.close()
        }
    }

    @Test
    fun `prepared text resources stay closed when readback close is quarantined and retried`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(),
        )
        val registry = GPURuntimeResourceAdapter()
        var ownership: GPUPreparedNativeFrameOwnership? = null
        try {
            val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                fixture.materialize(),
            ).draft
            val payload = draft.payload
            val atlas = assertIs<GPUPreparedNativeScopeOperand.TextureUpload>(
                payload.scopeOperands.first {
                    it is GPUPreparedNativeScopeOperand.TextureUpload &&
                        it.uploadRole == "prepared-text-r8"
                },
            ).destination.texture
            val readback = payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
                .single()
                .destination.buffer
            ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                registry.registerPreparedNativeFrameDraft(draft),
            ).ownership
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                ownership.bindLateSurface(
                    null,
                    GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
                ownership.consume(payload.identity),
            )
            assertTrue(ownership.markSubmitted())
            assertTrue(ownership.releaseAfterCompletion())
            assertEquals(1, fixture.native.closeCounts[atlas])
            assertTrue(ownership.claimOutputMapping())
            fixture.native.failCloseOnce("Kanvas.frame.preparedSurface.readback")

            assertFalse(ownership.releaseOutputAfterReadback())
            assertEquals(1, fixture.native.closeCounts[atlas])
            assertEquals(
                1,
                fixture.native.closeAttempts("Kanvas.frame.preparedSurface.readback"),
            )
            registry.close()
            assertEquals(1, fixture.native.closeCounts[atlas])
            assertEquals(2, fixture.native.closeCounts[readback])
        } finally {
            ownership?.rollback()
            runCatching { registry.close() }
            fixture.close()
        }
    }

    @Test
    fun `image only materializer emits the exact upload render and readback partition`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.ImageOnly)
        try {
            val result = fixture.materialize()
            val materialized = assertIs<
                GPUPreparedNativeFramePayloadMaterialization.Materialized
                >(result, result.toString())
            val payload = materialized.draft.payload

            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.sourceStepIndex },
                payload.scopeOperands.map { it.sourceStepIndex },
            )
            assertEquals(
                listOf(
                    GPUPreparedNativeScopeOperand.TextureUpload::class,
                    GPUPreparedNativeScopeOperand.Render::class,
                    GPUPreparedNativeScopeOperand.Readback::class,
                ),
                payload.scopeOperands.map { it::class },
            )
            val render = assertIs<GPUPreparedNativeScopeOperand.Render>(
                payload.scopeOperands[1],
            )
            assertTrue(
                render.semanticPayloads.all { it is GPUDrawSemanticPayload.SampledImage },
            )
            assertEquals(null, payload.leaseLifecycle)
            assertTrue(payload.pathDepthStencilViewAuthority.isEmpty())
            assertTrue(materialized.draft.disposeBeforeRegistration())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `mixed materializer composes both lattices in the exact full encoder order`() {
        listOf(
            PreparedSurfaceFixtureShape.CoreImageCore,
            PreparedSurfaceFixtureShape.ImageCoreImage,
        ).forEach { shape ->
            val fixture = fixture(shape)
            try {
                val result = fixture.materialize()
                val materialized = assertIs<
                    GPUPreparedNativeFramePayloadMaterialization.Materialized
                    >(result, result.toString())
                val payload = materialized.draft.payload

                assertEquals(
                    fixture.input.encoderPlan.scopes.map { it.sourceStepIndex },
                    payload.scopeOperands.map { it.sourceStepIndex },
                    shape.name,
                )
                assertEquals(
                    fixture.input.encoderPlan.scopes.map { it.operationKind },
                    payload.scopeOperands.map { it.operationKind },
                    shape.name,
                )
                assertEquals(
                    fixture.input.encoderPlan.scopes.map { it.nativeOperandKeys },
                    payload.scopeOperandKeys,
                    shape.name,
                )
                assertEquals(
                    fixture.input.encoderPlan.scopes.map { scope ->
                        GPUPreparedNativeScopeKey(
                            scope.sourceStepIndex,
                            scope.operationKind,
                            scope.resourceGenerationLabels,
                            scope.nativeOperandKeys,
                        )
                    },
                    payload.identity.scopes,
                    shape.name,
                )
                assertTrue(
                    payload.scopeOperands.none {
                        it is GPUPreparedNativeScopeOperand.PreparedImageRenderRun
                    },
                    "a production payload must contain only target-bound Render image operands",
                )

                val uploads = payload.scopeOperands.filterIsInstance<
                    GPUPreparedNativeScopeOperand.TextureUpload
                    >()
                val imageConsumers = payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                    .filter { render ->
                        render.semanticPayloads.all {
                            it is GPUDrawSemanticPayload.SampledImage
                        }
                    }
                assertEquals(1, uploads.size, "the shared image must be uploaded once")
                assertEquals(
                    if (shape == PreparedSurfaceFixtureShape.ImageCoreImage) 2 else 1,
                    imageConsumers.size,
                )
                assertTrue(imageConsumers.all {
                    uploads.single().sourceStepIndex < it.sourceStepIndex
                })

                assertEquals(
                    Triple(1L, 0L, 1L),
                    fixture.targetLifecycle.snapshot(),
                    "one canonical target must be created once and borrowed once",
                )
                assertTrue(materialized.draft.disposeBeforeRegistration())
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `real dispatcher materializes and encodes core image core without compatibility detour`() {
        val input = capturedPreparedSurfaceInputs(
            shape = PreparedSurfaceFixtureShape.CoreImageCore,
            includeReadback = true,
        )
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val targetLifecycle = GPUWgpu4kPreparedSceneTargetLifecycle()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            device = native.device,
            width = 16,
            height = 16,
            format = GPUTextureFormat.RGBA8UnormSrgb,
            deviceGeneration = input.generationSeal.deviceGeneration,
            targetGeneration = input.generationSeal.targetGeneration,
            lifecycle = targetLifecycle,
            setupTransaction = setup,
        )
        setup.commit()
        val solidRectCache = GPUWgpu4kSolidRectSessionCache(native.device)
        val coreCache = GPUWgpu4kCorePrimitiveSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val registeredUniformRectCache = GPUWgpu4kRegisteredUniformRectSessionCache(native.device)
        val blurCache = GPUWgpu4kSeparableBlurRectSessionCache(native.device)
        val destinationCopyCache = GPUWgpu4kDestinationCopySessionCache(native.device)
        val imageCache = GPUWgpu4kPreparedImageSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val textCache = GPUWgpu4kPreparedTextSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val colorCache = GPUWgpu4kColorGlyphSessionCache(native.device)
        val surfaceCache = GPUWgpu4kSurfaceBlitSessionCache(native.device, target)
        val mixed = GPUWgpu4kPreparedSurfaceFramePayloadMaterializer(
            device = native.device,
            queue = native.queue,
            preparedSceneTarget = target,
            corePrimitiveCache = coreCache,
            preparedImageCache = imageCache,
            preparedTextCache = textCache,
            colorGlyphCache = colorCache,
            preparedImageHandleFactory = GPUWgpu4kPreparedImageNativeHandleFactory(native.device),
            preparedImageCapabilities = preparedImageCapabilities(),
            surfaceBlitCache = surfaceCache,
            corePrimitiveLimits = LIMITS,
        )
        val dispatcher = GPUWgpu4kFramePayloadMaterializerDispatcher(
            device = native.device,
            queue = native.queue,
            preparedSceneTarget = target,
            solidRectCache = solidRectCache,
            corePrimitiveCache = coreCache,
            registeredUniformRectCache = registeredUniformRectCache,
            separableBlurRectCache = blurCache,
            maskBlurCache = GPUWgpu4kMaskBlurSessionCache(native.device),
            destinationCopyCache = destinationCopyCache,
            surfaceBlitCache = surfaceCache,
            corePrimitiveLimits = LIMITS,
            preparedSurfaceMixedMaterializer = mixed,
        )
        val backend = GPUWgpu4kFrameEncodingBackend(
            deviceGeneration = input.generationSeal.deviceGeneration,
            device = native.device,
            queue = native.queue,
            canonicalSceneTargetView = target.view,
        )
        val registry = GPURuntimeResourceAdapter()
        val encodingWitness = GPUFrameCoreTestFixture.preparedFrame()
        var draft: GPUPreparedNativeFrameDraft? = null
        var ownership: GPUPreparedNativeFrameOwnership? = null
        try {
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                dispatcher.materializeReusable(
                    input.framePlan,
                    input.encoderPlan,
                    input.resources,
                    input.generationSeal,
                ),
            )
            draft = materialized.draft
            val payload = materialized.draft.payload
            val imageUploadStep = input.framePlan.steps
                .filterIsInstance<GPUFrameStep.UploadResourceStep>()
                .single { it.imageResourcePlan != null }
            val imageResourcePlan = requireNotNull(imageUploadStep.imageResourcePlan)
            val textureUpload = assertIs<GPUPreparedNativeScopeOperand.TextureUpload>(
                payload.scopeOperands.single {
                    it is GPUPreparedNativeScopeOperand.TextureUpload
                },
            )

            assertTrue(
                native.writeBufferCalls.any {
                    it.bufferLabel == "Kanvas.frame.preparedImage.uniforms"
                },
                "prepared-image uniforms must be uploaded during materialization",
            )
            val writeBufferCountAfterMaterialization = native.writeBufferCalls.size
            assertEquals(0, native.writeTextureCalls)
            assertEquals(imageResourcePlan.uploadLayout, textureUpload.layout)
            kotlin.test.assertContentEquals(
                imageResourcePlan.uploadLayout.bytesForUpload(),
                textureUpload.data.bytes(),
            )
            assertEquals(
                gpuPreparedNativeBindingKey(
                    "prepared-image-upload-data:${imageUploadStep.staging.value}",
                ),
                textureUpload.data.key.bindingKey,
            )
            assertTrue(
                payload.scopeOperands.none {
                    it is GPUPreparedNativeScopeOperand.PreparedImageRenderRun
                },
            )
            assertTrue(
                input.framePlan.steps.none {
                    it is GPUFrameStep.CopyDestinationStep ||
                        it is GPUFrameStep.CopyAsDrawMaterializationStep
                },
                "the main route must not create a compatibility copy step",
            )
            assertTrue(
                input.encoderPlan.scopes.none {
                    it.operationKind == GPUEncoderOperationKind.CopyDestination ||
                        it.operationKind == GPUEncoderOperationKind.CopyAsDraw
                },
                "the main route must not encode a compatibility copy operation",
            )
            assertEquals(input.encoderPlan.scopes.size, payload.scopeOperands.size)
            assertEquals(
                input.encoderPlan.scopes.map { it.nativeOperandKeys },
                payload.scopeOperandKeys,
            )
            val registeredOwnership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                registry.registerPreparedNativeFrameDraft(materialized.draft),
            ).ownership
            ownership = registeredOwnership
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                registeredOwnership.bindLateSurface(
                    acquiredSurface = null,
                    binding = GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
                registeredOwnership.consume(payload.identity),
            )

            val encoder = backend.createCommandEncoder("prepared-surface-e2e")
            input.encoderPlan.scopes.zip(payload.scopeOperands).forEach { (scope, operand) ->
                assertEquals(scope.sourceStepIndex, operand.sourceStepIndex)
                assertEquals(scope.operationKind, operand.operationKind)
                encoder.encode(
                    scope,
                    encodingWitness,
                    GPUFrameCoreTestFixture.sceneTarget(
                        targetGeneration = input.generationSeal.targetGeneration,
                    ),
                    operand,
                )
            }
            assertEquals(writeBufferCountAfterMaterialization, native.writeBufferCalls.size)
            assertEquals(1, native.writeTextureCalls)
            assertEquals(listOf("core", "image", "core"), native.renderPipelineKinds)
            assertEquals(1, native.readbackCopyCalls)

            val commandBuffer = encoder.finish()
            backend.submit(commandBuffer)
            assertTrue(
                native.events.indexOf("encoder.finish") <
                    native.events.indexOf("queue.submit"),
            )
            assertEquals(1, native.closeAttempts("frame.commandEncoder"))
            assertEquals(1, native.closeAttempts("frame.commandBuffer"))

            assertTrue(registeredOwnership.markSubmitted())
            assertTrue(registeredOwnership.releaseAfterCompletion())
            assertTrue(registeredOwnership.claimOutputMapping())
            assertTrue(registeredOwnership.releaseOutputAfterReadback())
            val closeCountsAfterCompletion = native.closeCounts.toMap()
            assertFalse(registeredOwnership.releaseAfterCompletion())
            assertFalse(registeredOwnership.releaseOutputAfterReadback())
            assertEquals(closeCountsAfterCompletion, native.closeCounts)
            assertTrue(native.closeCounts.values.all { it == 1 })
        } finally {
            ownership?.rollback() ?: draft?.disposeBeforeRegistration()
            if (encodingWitness.claimForRollback()) encodingWitness.rollback.execute()
            runCatching { registry.close() }
            runCatching { backend.close() }
            runCatching { dispatcher.close() }
            runCatching { surfaceCache.close() }
            runCatching { imageCache.close() }
            runCatching { destinationCopyCache.close() }
            runCatching { blurCache.close() }
            runCatching { registeredUniformRectCache.close() }
            runCatching { coreCache.close() }
            runCatching { solidRectCache.close() }
            runCatching { target.close() }
        }
    }

    @Test
    fun `global refusal creates no native handle and retains no draft or owner`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.CoreImageCore)
        try {
            val nativeEventCount = fixture.native.events.size
            val imageHandleCount = fixture.imageFactory.handleCreates
            val staleSeal = GPUPreparedGenerationSeal(
                deviceGeneration = GPUDeviceGenerationID(
                    fixture.input.generationSeal.deviceGeneration.value + 1L,
                ),
                targetGeneration = fixture.input.generationSeal.targetGeneration,
                resourceGenerations = fixture.input.generationSeal.resourceGenerations,
                capabilitySealHash = fixture.input.generationSeal.capabilitySealHash,
            )

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materializer.materializeReusable(
                    fixture.input.framePlan,
                    fixture.input.encoderPlan,
                    fixture.input.resources,
                    staleSeal,
                ),
            )

            assertEquals(nativeEventCount, fixture.native.events.size)
            assertEquals(imageHandleCount, fixture.imageFactory.handleCreates)
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            assertEquals(null, refused.retainedCloseOwner)
            assertEquals(Triple(1L, 0L, 0L), fixture.targetLifecycle.snapshot())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `WGSL refusal crosses mixed materializer before target borrow or image allocation`() {
        val fixture = fixture(
            PreparedSurfaceFixtureShape.CoreImageCore,
            shaderSource = "@fragment fn broken(",
        )
        try {
            val nativeEventCount = fixture.native.events.size
            val imageHandleCount = fixture.imageFactory.handleCreates

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )

            assertEquals(GPUPreparedImageRefusalCodes.WGSL_VALIDATION, refused.code)
            assertEquals(nativeEventCount, fixture.native.events.size)
            assertEquals(imageHandleCount, fixture.imageFactory.handleCreates)
            assertEquals(Triple(1L, 0L, 0L), fixture.targetLifecycle.snapshot())
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedCloseOwner)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `unexpected text coverage mask bind group refuses before every native allocation`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.CoreImageText)
        try {
            val textStepIndex = fixture.input.framePlan.steps.indexOfFirst { step ->
                step is GPUFrameStep.RenderPassStep &&
                    step.drawPackets.all { packet ->
                        packet.semanticPayload is GPUDrawSemanticPayload.TextA8
                    }
            }
            val originalScope = fixture.input.encoderPlan.scopes.single { scope ->
                scope.sourceStepIndex == textStepIndex
            }
            val packetId = (fixture.input.framePlan.steps[textStepIndex] as
                GPUFrameStep.RenderPassStep).drawPackets.single().packetId.value
            val forgedMaskKey = GPUPreparedNativeOperandKey(
                GPUPreparedNativeOperandRole.RenderBindGroup,
                GPUPreparedNativeOperandKind.BindGroup,
                gpuPreparedNativeBindingKey(
                    "prepared-text:$packetId:coverage-mask-group",
                ),
            )
            val mutatedScope = GPUCommandEncoderScopePlan(
                sourceStepIndex = originalScope.sourceStepIndex,
                operationKind = originalScope.operationKind,
                scopeLabel = originalScope.scopeLabel,
                sourceTaskIds = originalScope.sourceTaskIds,
                sourcePacketIds = originalScope.sourcePacketIds,
                facadeOperationClasses = originalScope.facadeOperationClasses,
                targetGeneration = originalScope.targetGeneration,
                resourceGenerationLabels = originalScope.resourceGenerationLabels,
                passCommandStream = originalScope.passCommandStream,
                corePrimitiveDirectNativeRouteSeal =
                    originalScope.corePrimitiveDirectNativeRouteSeal,
                corePrimitivePathStencilNativeRouteSeal =
                    originalScope.corePrimitivePathStencilNativeRouteSeal,
                corePrimitiveNativeScopeRouteSeal =
                    originalScope.corePrimitiveNativeScopeRouteSeal,
                corePrimitiveClipStencilPreparedRouteSeal =
                    originalScope.corePrimitiveClipStencilPreparedRouteSeal,
                corePrimitiveCoverageMaskPreparedRouteSeal =
                    originalScope.corePrimitiveCoverageMaskPreparedRouteSeal,
                targetResource = originalScope.targetResource,
            ).attachNativeOperandKeys(
                originalScope.nativeOperandKeys.dropLast(1) +
                    forgedMaskKey +
                    originalScope.nativeOperandKeys.last(),
            )
            val mutatedEncoder = GPUCommandEncoderPlan.ordered(
                planId = fixture.input.encoderPlan.planId,
                contextIdentity = fixture.input.encoderPlan.contextIdentity,
                deviceGeneration = fixture.input.encoderPlan.deviceGeneration,
                targetGeneration = fixture.input.encoderPlan.targetGeneration,
                scopes = fixture.input.encoderPlan.scopes.map { scope ->
                    if (scope === originalScope) mutatedScope else scope
                },
            )
            val eventsBefore = fixture.native.events.toList()
            val writesBefore = fixture.native.writeBufferCalls.toList()
            val imageHandlesBefore = fixture.imageFactory.handleCreates

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materializer.materializeReusable(
                    fixture.input.framePlan,
                    mutatedEncoder,
                    fixture.input.resources,
                    fixture.input.generationSeal,
                ),
            )

            assertEquals("invalid.prepared-surface.encoder-plan", refused.code)
            assertEquals(eventsBefore, fixture.native.events)
            assertEquals(writesBefore, fixture.native.writeBufferCalls)
            assertEquals(imageHandlesBefore, fixture.imageFactory.handleCreates)
            assertEquals(Triple(1L, 0L, 0L), fixture.targetLifecycle.snapshot())
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            assertEquals(null, refused.retainedCloseOwner)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `permuted image bindings are refused before target borrow or native allocation`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.ImageCoreImage)
        try {
            val malformedInput = fixture.input.copy(
                framePlan = fixture.input.framePlan.withReversedPreparedImageBindings(),
            )
            val nativeEventCount = fixture.native.events.size
            val imageHandleCount = fixture.imageFactory.handleCreates

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materializer.materializeReusable(
                    malformedInput.framePlan,
                    malformedInput.encoderPlan,
                    malformedInput.resources,
                    malformedInput.generationSeal,
                ),
            )

            assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
            assertEquals(nativeEventCount, fixture.native.events.size)
            assertEquals(imageHandleCount, fixture.imageFactory.handleCreates)
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            assertEquals(null, refused.retainedCloseOwner)
            assertEquals(Triple(1L, 0L, 0L), fixture.targetLifecycle.snapshot())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `one successful materialization creates one draft with one readback and one close ledger`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.ImageCoreImage)
        try {
            val result = fixture.materialize()
            val materialized = assertIs<
                GPUPreparedNativeFramePayloadMaterialization.Materialized
                >(result, result.toString())
            val draft = materialized.draft

            assertEquals(
                1,
                draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
                    .size,
            )
            assertEquals(
                draft.pendingOwnedHandlesSnapshot().distinctBy(System::identityHashCode).size,
                draft.pendingOwnedHandlesSnapshot().size,
            )

            assertTrue(draft.disposeBeforeRegistration())
            assertTrue(draft.disposeBeforeRegistration())
            assertTrue(draft.pendingOwnedHandlesSnapshot().isEmpty())
            assertTrue(fixture.imageFactory.closeCounts.values.all { it == 1 })
            assertTrue(fixture.native.closeCounts.values.all { it <= 1 })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `failure after R8 creation rolls back its texture and view exactly once`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageOnly,
            inputOverride = capturedPreparedTextInputs(),
        )
        try {
            fixture.native.fail("createBuffer", ordinal = 1)

            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )

            val texture = fixture.native
                .createdHandles("Kanvas.frame.preparedText.r8-page")
                .single()
            val view = fixture.native
                .createdHandles("Kanvas.frame.preparedText.r8-page-view")
                .single()
            assertEquals(1, fixture.native.closeCounts[texture])
            assertEquals(1, fixture.native.closeCounts[view])
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `surface composition appends one late bound surface scope to the same draft`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.CoreImageCore,
            includeSurface = true,
        )
        try {
            val result = fixture.materialize()
            val materialized = assertIs<
                GPUPreparedNativeFramePayloadMaterialization.Materialized
                >(result, result.toString())
            val draft = materialized.draft
            val output = fixture.input.framePlan.steps
                .filterIsInstance<GPUFrameStep.AcquireSurfaceOutput>()
                .single()
                .descriptor
                .output
            val acquired = GPUAcquiredSurfaceOutput(
                output = output,
                deviceGeneration = fixture.input.generationSeal.deviceGeneration,
                targetGeneration = fixture.input.generationSeal.targetGeneration,
                evidenceLabel = "prepared-surface-materializer-test",
            )
            val binding = assertIs<GPUPreparedNativeFrameLateSurfaceBinding.Bound>(
                fixture.materializer.bindLateSurface(draft, acquired),
            )

            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.sourceStepIndex },
                draft.payload.scopeOperands.map { it.sourceStepIndex },
            )
            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.operationKind },
                draft.payload.scopeOperands.map { it.operationKind },
            )
            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.nativeOperandKeys },
                draft.payload.scopeOperandKeys,
            )
            assertEquals(
                1,
                draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
                    .size,
            )
            assertEquals(
                1,
                draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.SurfaceBlit>()
                    .size,
            )
            assertTrue(draft.payload.bindLateSurface(acquired, binding))
            assertFalse(draft.payload.bindLateSurface(acquired, binding))
            assertTrue(draft.payload.lateSurfaceReady)
            assertEquals(1L, fixture.targetLifecycle.snapshot().first)
            assertTrue(draft.disposeBeforeRegistration())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `mixed late surface refusals keep stable codes and allocate no native state`() {
        data class Case(
            val label: String,
            val expectedCode: String,
            val surfaceTargetAvailable: Boolean = true,
            val surfaceTargetGenerationDelta: Long = 0L,
            val acquired: (Fixture, GPUSurfaceOutputRef) -> GPUAcquiredSurfaceOutput?,
        )
        val cases = listOf(
            Case(
                label = "missing acquired output",
                expectedCode = "unsupported.native-frame-payload.surface-output-missing",
                acquired = { _, _ -> null },
            ),
            Case(
                label = "mismatched acquired output",
                expectedCode = "stale.native-frame-payload.surface-output-mismatch",
                acquired = { fixture, _ ->
                    fixture.acquiredSurface(GPUSurfaceOutputRef("surface.other"))
                },
            ),
            Case(
                label = "native target unavailable",
                expectedCode = "unsupported.native-frame-payload.surface-target-unavailable",
                surfaceTargetAvailable = false,
                acquired = { fixture, output -> fixture.acquiredSurface(output) },
            ),
            Case(
                label = "stale native target generation",
                expectedCode = "stale.native-frame-payload.surface-target-generation",
                surfaceTargetGenerationDelta = 1L,
                acquired = { fixture, output -> fixture.acquiredSurface(output) },
            ),
        )

        cases.forEach { case ->
            val fixture = fixture(
                shape = PreparedSurfaceFixtureShape.CoreImageCore,
                includeSurface = true,
                surfaceTargetAvailable = case.surfaceTargetAvailable,
                surfaceTargetGenerationDelta = case.surfaceTargetGenerationDelta,
            )
            try {
                val materialized = assertIs<
                    GPUPreparedNativeFramePayloadMaterialization.Materialized
                    >(fixture.materialize(), case.label)
                val draft = materialized.draft
                val output = fixture.surfaceOutput()
                val nativeEventsBefore = fixture.native.events.toList()
                val imageCreatesBefore = fixture.imageFactory.handleCreates
                val handlesBefore = draft.pendingOwnedHandlesSnapshot()

                val refused = assertIs<GPUPreparedNativeFrameLateSurfaceBinding.Refused>(
                    fixture.materializer.bindLateSurface(
                        draft,
                        case.acquired(fixture, output),
                    ),
                    case.label,
                )

                assertEquals(case.expectedCode, refused.code, case.label)
                assertEquals(nativeEventsBefore, fixture.native.events, case.label)
                assertEquals(imageCreatesBefore, fixture.imageFactory.handleCreates, case.label)
                val handlesAfter = draft.pendingOwnedHandlesSnapshot()
                assertEquals(handlesBefore.size, handlesAfter.size, case.label)
                assertTrue(
                    handlesBefore.zip(handlesAfter).all { (before, after) -> before === after },
                    case.label,
                )
                assertFalse(draft.payload.lateSurfaceReady, case.label)
                assertTrue(draft.disposeBeforeRegistration(), case.label)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `duplicate mixed late surface bind is refused by the real registry without allocation`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.CoreImageCore,
            includeSurface = true,
        )
        val adapter = GPURuntimeResourceAdapter()
        try {
            val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                fixture.materialize(),
            ).draft
            val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
            val boundary = adapter.bindNativeFrameBoundary(provider, fixture.materializer)
            val registration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                boundary.register(draft),
            )
            val acquired = fixture.acquiredSurface(fixture.surfaceOutput())

            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                boundary.bindLateSurface(registration.ownership, draft, acquired),
            )
            val nativeEventsBeforeDuplicate = fixture.native.events.toList()
            val imageCreatesBeforeDuplicate = fixture.imageFactory.handleCreates

            val duplicate = assertIs<GPUPreparedNativeFrameBindingResult.Refused>(
                boundary.bindLateSurface(registration.ownership, draft, acquired),
            )

            assertEquals("unsupported.native-frame-payload.draft-state", duplicate.code)
            assertEquals(nativeEventsBeforeDuplicate, fixture.native.events)
            assertEquals(imageCreatesBeforeDuplicate, fixture.imageFactory.handleCreates)
            assertTrue(registration.ownership.rollback())
        } finally {
            adapter.close()
            fixture.close()
        }
    }

    @Test
    fun `exception after global preflight closes each frame owned image handle once`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageCoreImage,
            failImageBindGroup = true,
        )
        try {
            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )

            assertTrue(fixture.imageFactory.handleCreates > 0)
            assertTrue(fixture.imageFactory.closeCounts.isNotEmpty())
            assertTrue(fixture.imageFactory.closeCounts.values.all { it == 1 })
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            assertEquals(null, refused.retainedCloseOwner)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `image allocation rollback retains only the handle whose first close failed`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageCoreImage,
            failImageBindGroup = true,
            failFirstImageClose = true,
        )
        try {
            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )
            val retainedOwner = requireNotNull(refused.retainedCloseOwner)
            val closeCountsAfterRefusal = fixture.imageFactory.closeCounts.toMap()
            val failedHandle = requireNotNull(fixture.imageFactory.failedCloseLabel)
            assertTrue(closeCountsAfterRefusal.values.all { it == 1 })
            assertTrue(refused.message.contains("Rollback retained native ownership"))

            retainedOwner.close()

            assertEquals(2, fixture.imageFactory.closeCounts.getValue(failedHandle))
            assertTrue(
                fixture.imageFactory.closeCounts
                    .filterKeys { it != failedHandle }
                    .values
                    .all { it == 1 },
                "handles closed successfully during rollback must not be closed twice",
            )
        } finally {
            fixture.close()
        }
    }

    private fun destinationReadPreparedTextInputs(
        coverageMask: Boolean = false,
    ): CapturedPreparedSurfaceInputs {
        val bounds = GPUPixelBounds(0, 0, 16, 16)
        val clip = if (coverageMask) {
            GPUClipExecutionPlan.CoverageMask(
                contentKey = "clip:color-glyph-destination-mask",
                bounds = bounds,
                sampleCount = 1,
                depthStencilRequired = false,
                orderingToken = GPUClipOrderingToken(
                    "clip-order:color-glyph-destination-mask",
                ),
                producers = listOf(
                    GPUClipMaskProducerPlan(
                        sourceOrder = 0,
                        geometry = GPUClipExecutionGeometry.Rect(
                            GPUBounds(0f, 0f, 16f, 16f),
                        ),
                        combine = GPUClipMaskCombine.Intersect,
                        antiAlias = false,
                    ),
                    GPUClipMaskProducerPlan(
                        sourceOrder = 1,
                        geometry = GPUClipExecutionGeometry.Rect(
                            GPUBounds(2f, 2f, 4f, 4f),
                        ),
                        combine = GPUClipMaskCombine.Difference,
                        antiAlias = false,
                    ),
                ),
                consumer = GPUClipMaskConsumerPlan(),
            )
        } else {
            GPUClipExecutionPlan.AnalyticCoverage(
                geometry = GPUClipExecutionGeometry.Rect(
                    GPUBounds(0.25f, 0.5f, 15.75f, 15.5f),
                ),
                scissor = bounds,
                antiAlias = true,
            )
        }
        return capturedPreparedTextInputs(
            commandIds = listOf(0),
            textInstanceCounts = listOf(1),
            colorGlyphCommandIds = setOf(0),
            blendPlan = GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.COLOR_DODGE,
                formulaId = "color_dodge@v1",
                sourceCoverageEncoding =
                    GPUSourceCoverageEncoding.ScalarCoverageInShader,
            ),
            colorGlyphClipExecutionPlan = clip,
            colorGlyphClipIdentity = if (coverageMask) {
                "clip-semantic:color-glyph-destination-mask"
            } else {
                "clip-semantic:color-glyph-destination-analytic"
            },
        )
    }

    private fun fixture(
        shape: PreparedSurfaceFixtureShape,
        includeSurface: Boolean = false,
        failImageBindGroup: Boolean = false,
        failFirstImageClose: Boolean = false,
        surfaceTargetAvailable: Boolean = true,
        surfaceTargetGenerationDelta: Long = 0L,
        shaderSource: String = GPU_PREPARED_IMAGE_WGSL,
        inputOverride: CapturedPreparedSurfaceInputs? = null,
        onCacheAcquire: () -> Unit = {},
        onTargetBorrow: () -> Unit = {},
        onDestinationSnapshotViewCreated: () -> Unit = {},
    ): Fixture {
        val input = inputOverride ?: capturedPreparedSurfaceInputs(
            shape = shape,
            includeReadback = true,
            includeSurface = includeSurface,
        )
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val targetLifecycle = GPUWgpu4kPreparedSceneTargetLifecycle()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            device = native.device,
            width = 16,
            height = 16,
            format = GPUTextureFormat.RGBA8UnormSrgb,
            deviceGeneration = input.generationSeal.deviceGeneration,
            targetGeneration = input.generationSeal.targetGeneration,
            lifecycle = targetLifecycle,
            setupTransaction = setup,
        )
        setup.commit()
        val coreCache = GPUWgpu4kCorePrimitiveSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val imageCache = GPUWgpu4kPreparedImageSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val textCache = GPUWgpu4kPreparedTextSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val imageFactory = CompositePreparedImageHandleFactory(
            failFirstClose = failFirstImageClose,
        )
        val colorCache = GPUWgpu4kColorGlyphSessionCache(native.device)
        val selectedImageFactory = if (failImageBindGroup) {
            FailOnFirstBindGroupPreparedImageHandleFactory(imageFactory)
        } else {
            imageFactory
        }
        val surfaceCache = GPUWgpu4kSurfaceBlitSessionCache(native.device, target)
        val lateSurfaceTarget = fakeNativeHandle<GPUTextureView>("late-surface-target")
        val materializer = GPUWgpu4kPreparedSurfaceFramePayloadMaterializer(
            device = native.device,
            queue = native.queue,
            preparedSceneTarget = target,
            corePrimitiveCache = coreCache,
            preparedImageCache = imageCache,
            preparedTextCache = textCache,
            colorGlyphCache = colorCache,
            preparedImageHandleFactory = selectedImageFactory,
            preparedImageCapabilities = preparedImageCapabilities(),
            surfaceBlitCache = surfaceCache,
            surfaceTargetResolver = GPUAcquiredSurfaceNativeTargetResolver {
                if (!surfaceTargetAvailable) {
                    null
                } else {
                    GPUPreparedNativeTextureViewOperand(
                        lateSurfaceTarget,
                        GPUDeviceGenerationID(
                            input.generationSeal.deviceGeneration.value +
                                surfaceTargetGenerationDelta,
                        ),
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    )
                }
            },
            corePrimitiveLimits = LIMITS,
            onCacheAcquire = onCacheAcquire,
            onTargetBorrow = onTargetBorrow,
            onDestinationSnapshotViewCreated = onDestinationSnapshotViewCreated,
            preflight = GPUPreparedSurfaceNativePreflight(shaderSource),
        )
        return Fixture(
            input,
            native,
            targetLifecycle,
            target,
            coreCache,
            imageCache,
            textCache,
            colorCache,
            surfaceCache,
            imageFactory,
            materializer,
        )
    }

    private data class DestinationAuthorityMutation(
        val name: String,
        val coverageMask: Boolean = false,
        val mutate: (CapturedPreparedSurfaceInputs) -> CapturedPreparedSurfaceInputs,
    )

    private data class Fixture(
        val input: CapturedPreparedSurfaceInputs,
        val native: GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy,
        val targetLifecycle: GPUWgpu4kPreparedSceneTargetLifecycle,
        val target: GPUWgpu4kPreparedSceneTarget,
        val coreCache: GPUWgpu4kCorePrimitiveSessionCache,
        val imageCache: GPUWgpu4kPreparedImageSessionCache,
        val textCache: GPUWgpu4kPreparedTextSessionCache,
        val colorCache: GPUWgpu4kColorGlyphSessionCache,
        val surfaceCache: GPUWgpu4kSurfaceBlitSessionCache,
        val imageFactory: CompositePreparedImageHandleFactory,
        val materializer: GPUWgpu4kPreparedSurfaceFramePayloadMaterializer,
    ) {
        fun materialize(): GPUPreparedNativeFramePayloadMaterialization =
            materializer.materializeReusable(
                input.framePlan,
                input.encoderPlan,
                input.resources,
                input.generationSeal,
            )

        fun surfaceOutput(): GPUSurfaceOutputRef =
            input.framePlan.steps
                .filterIsInstance<GPUFrameStep.AcquireSurfaceOutput>()
                .single()
                .descriptor
                .output

        fun acquiredSurface(output: GPUSurfaceOutputRef): GPUAcquiredSurfaceOutput =
            GPUAcquiredSurfaceOutput(
                output = output,
                deviceGeneration = input.generationSeal.deviceGeneration,
                targetGeneration = input.generationSeal.targetGeneration,
                evidenceLabel = "prepared-surface-materializer-test",
            )

        fun close() {
            runCatching { materializer.close() }
            runCatching { surfaceCache.close() }
            runCatching { imageCache.close() }
            runCatching { textCache.close() }
            runCatching { colorCache.close() }
            runCatching { coreCache.close() }
            runCatching { target.close() }
        }
    }

    private class FailOnFirstBindGroupPreparedImageHandleFactory(
        private val delegate: CompositePreparedImageHandleFactory,
    ) : GPUPreparedImageNativeHandleFactory {
        override fun createTexture(request: GPUImageFrameResourcePlan): GPUTexture =
            delegate.createTexture(request)

        override fun createTextureView(
            texture: GPUTexture,
            request: GPUImageFrameResourcePlan,
        ): GPUTextureView = delegate.createTextureView(texture, request)

        override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler =
            delegate.createSampler(descriptor)

        override fun createUniformBuffer(size: Long): GPUBuffer =
            delegate.createUniformBuffer(size)

        override fun createBindGroup(
            bindGroupLayout: GPUBindGroupLayout,
            request: GPUImageBindingRequest,
            uniformBuffer: GPUBuffer,
            textureView: GPUTextureView,
            sampler: GPUSampler,
        ): GPUBindGroup = error("injected prepared-image bind-group failure")
    }

    private class CompositePreparedImageHandleFactory(
        private val failFirstClose: Boolean,
    ) : GPUPreparedImageNativeHandleFactory {
        val closeCounts = linkedMapOf<String, Int>()
        var handleCreates = 0
        var failedCloseLabel: String? = null
        private var nextOrdinal = 0
        private var closeFailurePending = failFirstClose

        override fun createTexture(request: GPUImageFrameResourcePlan): GPUTexture =
            handle("texture")

        override fun createTextureView(
            texture: GPUTexture,
            request: GPUImageFrameResourcePlan,
        ): GPUTextureView = handle("view")

        override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler =
            handle("sampler")

        override fun createUniformBuffer(size: Long): GPUBuffer = handle("uniform")

        override fun createBindGroup(
            bindGroupLayout: GPUBindGroupLayout,
            request: GPUImageBindingRequest,
            uniformBuffer: GPUBuffer,
            textureView: GPUTextureView,
            sampler: GPUSampler,
        ): GPUBindGroup = handle("bind-group")

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> handle(prefix: String): T {
            handleCreates += 1
            val label = "$prefix.${nextOrdinal++}"
            return Proxy.newProxyInstance(
                T::class.java.classLoader,
                arrayOf(T::class.java),
            ) { proxy, method, arguments ->
                when (method.name) {
                    "equals" -> proxy === arguments?.singleOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "close" -> {
                        closeCounts[label] = closeCounts.getOrDefault(label, 0) + 1
                        if (closeFailurePending) {
                            closeFailurePending = false
                            failedCloseLabel = label
                            error("injected first image close failure")
                        }
                    }
                    "setLabel" -> Unit
                    "getLabel", "toString" -> label
                    else -> null
                }
            } as T
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> fakeNativeHandle(label: String): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
                proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "close", "setLabel" -> Unit
                "getLabel", "toString" -> label
                else -> null
            }
        } as T

    private companion object {
        const val DESTINATION_SNAPSHOT_TEXTURE_LABEL =
            "Kanvas.frame.colorGlyph.destinationSnapshot"
        const val DESTINATION_SNAPSHOT_VIEW_LABEL =
            "Kanvas.frame.colorGlyph.destinationSnapshot-view"
        val LIMITS = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        )
    }
}

private fun GPUFramePlan.rebuiltForDestinationMutation(
    steps: List<GPUFrameStep> = this.steps,
    dependencies:
        List<org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency> =
        this.dependencies,
): GPUFramePlan = GPUFramePlan(
    frameId = frameId,
    capabilitySeal = capabilitySeal,
    recordingSeals = recordingSeals,
    steps = steps,
    memoryBudget = memoryBudget,
    diagnostics = diagnostics,
    dependencies = dependencies,
    phaseOrder = phaseOrder,
    elidedNoOpDraws = elidedNoOpDraws,
    atomicallyRefused = atomicallyRefused,
)

private fun GPUFrameStep.CopyDestinationStep.rebuiltForDestinationMutation(
    snapshot: GPUFrameTextureRef = this.snapshot,
    consumers: List<GPUDestinationSnapshotConsumerRef> = this.consumers,
): GPUFrameStep.CopyDestinationStep = GPUFrameStep.CopyDestinationStep(
    source = source,
    sourceKey = sourceKey,
    snapshot = snapshot,
    logicalBounds = logicalBounds,
    copyLayout = copyLayout,
    consumers = consumers,
    sourceTaskIds = sourceTaskIds,
)

private fun GPUFrameStep.RenderPassStep.rebuiltForDestinationMutation(
    target: GPUFrameTargetRef = this.target,
    resourceUses:
        List<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse> =
        this.resourceUses,
    drawPackets: List<GPUDrawPacket> = this.drawPackets,
    sourceTaskIds: List<GPUTaskID> = this.sourceTaskIds,
    batches:
        List<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch> =
        this.batches,
    preparedTextBindingsByPacketId:
        Map<
            GPUDrawPacketID,
            org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding,
            > = this.preparedTextBindingsByPacketId,
): GPUFrameStep.RenderPassStep = GPUFrameStep.RenderPassStep(
    target = target,
    loadStore = loadStore,
    samplePlan = samplePlan,
    resourceUses = resourceUses,
    drawPackets = drawPackets,
    sourceTaskIds = sourceTaskIds,
    batches = batches,
    sampleContinuation = sampleContinuation,
    depthStencilLoadStore = depthStencilLoadStore,
    preparedImageBindingsByPacketId = preparedImageBindingsByPacketId,
    preparedTextBindingsByPacketId = preparedTextBindingsByPacketId,
)

private fun GPUDrawPacket.rebuiltForDestinationMutation(
    blendPlan: GPUBlendPlan? = this.blendPlan,
    renderPipelineKey: GPURenderPipelineKey? = this.renderPipelineKey,
    bindingLayoutHash: String = this.bindingLayoutHash,
    semanticPayload: GPUDrawSemanticPayload? = this.semanticPayload,
    clipExecutionPlan: GPUClipExecutionPlan? = this.clipExecutionPlan,
): GPUDrawPacket = GPUDrawPacket(
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
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoveragePlan,
    clipExecutionPlan = clipExecutionPlan,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

private fun GPUFrameStep.RenderPassStep.withDestinationDrawPackets(
    packets: List<GPUDrawPacket>,
    sourceTaskIds: List<GPUTaskID> = this.sourceTaskIds,
    preparedTextBindingsByPacketId:
        Map<
            GPUDrawPacketID,
            org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding,
            > = this.preparedTextBindingsByPacketId,
): GPUFrameStep.RenderPassStep {
    val packetById = packets.associateBy(GPUDrawPacket::packetId)
    val rebuiltBatches = batches.map { batch ->
        org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch(
            batchId = batch.batchId,
            kind = batch.kind,
            packets = batch.packets.map { packet -> packetById.getValue(packet.packetId) },
            sourceTaskIds = if (sourceTaskIds == this.sourceTaskIds) {
                batch.sourceTaskIds
            } else {
                sourceTaskIds
            },
        )
    }
    return rebuiltForDestinationMutation(
        drawPackets = packets,
        sourceTaskIds = sourceTaskIds,
        batches = rebuiltBatches,
        preparedTextBindingsByPacketId = preparedTextBindingsByPacketId,
    )
}

private fun CapturedPreparedSurfaceInputs.withDestinationCopyMutation(
    transform: (GPUFrameStep.CopyDestinationStep) -> GPUFrameStep.CopyDestinationStep,
): CapturedPreparedSurfaceInputs {
    var mutations = 0
    val updated = framePlan.steps.map { step ->
        if (step is GPUFrameStep.CopyDestinationStep) {
            mutations += 1
            transform(step)
        } else {
            step
        }
    }
    require(mutations == 1)
    return copy(framePlan = framePlan.rebuiltForDestinationMutation(steps = updated))
}

private fun CapturedPreparedSurfaceInputs.withDestinationPacketMutation(
    transform: (GPUDrawPacket) -> GPUDrawPacket,
): CapturedPreparedSurfaceInputs {
    var mutations = 0
    val updated = framePlan.steps.map { step ->
        if (step !is GPUFrameStep.RenderPassStep) {
            step
        } else {
            val packets = step.drawPackets.map { packet ->
                if (packet.blendPlan is GPUBlendPlan.ShaderBlendWithDstRead) {
                    mutations += 1
                    transform(packet)
                } else {
                    packet
                }
            }
            if (packets == step.drawPackets) step else step.withDestinationDrawPackets(packets)
        }
    }
    require(mutations == 1)
    return copy(framePlan = framePlan.rebuiltForDestinationMutation(steps = updated))
}

private fun CapturedPreparedSurfaceInputs.withDestinationBindingMutation(
    transform:
        (
            org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding,
        ) -> org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding,
): CapturedPreparedSurfaceInputs {
    var mutations = 0
    val updated = framePlan.steps.map { step ->
        if (step !is GPUFrameStep.RenderPassStep ||
            step.drawPackets.none { it.blendPlan is GPUBlendPlan.ShaderBlendWithDstRead }
        ) {
            step
        } else {
            val packet = step.drawPackets.single {
                it.blendPlan is GPUBlendPlan.ShaderBlendWithDstRead
            }
            val binding = step.preparedTextBindingsByPacketId.getValue(packet.packetId)
            mutations += 1
            step.rebuiltForDestinationMutation(
                preparedTextBindingsByPacketId =
                    step.preparedTextBindingsByPacketId +
                        (packet.packetId to transform(binding)),
            )
        }
    }
    require(mutations == 1)
    return copy(framePlan = framePlan.rebuiltForDestinationMutation(steps = updated))
}

private fun CapturedPreparedSurfaceInputs.withMissingDestinationBinding():
    CapturedPreparedSurfaceInputs {
    var mutations = 0
    val updated = framePlan.steps.map { step ->
        if (step !is GPUFrameStep.RenderPassStep ||
            step.drawPackets.none { it.blendPlan is GPUBlendPlan.ShaderBlendWithDstRead }
        ) {
            step
        } else {
            val packet = step.drawPackets.single {
                it.blendPlan is GPUBlendPlan.ShaderBlendWithDstRead
            }
            val semantic = packet.semanticPayload as GPUDrawSemanticPayload.ColorGlyph
            val withoutMaterial = GPUDrawSemanticPayload.ColorGlyph(
                payloadRef = semantic.payloadRef,
                planArtifactKey = semantic.planArtifactKey,
                atlasArtifactKey = semantic.atlasArtifactKey,
                atlas = semantic.atlas,
                atlasFormat = semantic.atlasFormat,
                layers = semantic.layers,
                vertexData = semantic.vertexData,
                indexData = semantic.indexData,
                uniformBytes = semantic.uniformBytes,
                targetBounds = semantic.targetBounds,
                scissorBounds = semantic.scissorBounds,
                instances = semantic.instances,
                material = null,
                clipIdentity = semantic.clipIdentity,
                blendPlanIdentity = semantic.blendPlanIdentity,
                capabilitySnapshotHash = semantic.capabilitySnapshotHash,
                frameProvenance = semantic.frameProvenance,
                canonicalHash = semantic.canonicalHash,
            )
            val replaced = packet.rebuiltForDestinationMutation(
                semanticPayload = withoutMaterial,
            )
            mutations += 1
            step.withDestinationDrawPackets(
                packets = listOf(replaced),
                preparedTextBindingsByPacketId = emptyMap(),
            )
        }
    }
    require(mutations == 1)
    return copy(framePlan = framePlan.rebuiltForDestinationMutation(steps = updated))
}

private fun CapturedPreparedSurfaceInputs.withCoherentDestinationRenderTaskMutation():
    CapturedPreparedSurfaceInputs {
    val copy = framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>().single()
    val consumer = copy.consumers.single()
    val originalTaskId = consumer.renderTaskId
    val forgedTaskId = GPUTaskID("${originalTaskId.value}.forged")
    var renderMutations = 0
    val updatedSteps = framePlan.steps.map { step ->
        when (step) {
            is GPUFrameStep.CopyDestinationStep ->
                step.rebuiltForDestinationMutation(
                    consumers = step.consumers.map { reference ->
                        reference.copy(renderTaskId = forgedTaskId)
                    },
                )
            is GPUFrameStep.RenderPassStep ->
                if (step.drawPackets.any { packet -> packet.packetId == consumer.packetId }) {
                    renderMutations += 1
                    step.withDestinationDrawPackets(
                        packets = step.drawPackets,
                        sourceTaskIds = listOf(forgedTaskId),
                    )
                } else {
                    step
                }
            else -> step
        }
    }
    require(renderMutations == 1)
    val updatedDependencies = framePlan.dependencies.map { dependency ->
        dependency.copy(
            fromTaskId = if (dependency.fromTaskId == originalTaskId) {
                forgedTaskId
            } else {
                dependency.fromTaskId
            },
            toTaskId = if (dependency.toTaskId == originalTaskId) {
                forgedTaskId
            } else {
                dependency.toTaskId
            },
        )
    }
    return copy(
        framePlan = framePlan.rebuiltForDestinationMutation(
            steps = updatedSteps,
            dependencies = updatedDependencies,
        ),
    )
}

private fun CapturedPreparedSurfaceInputs.withPreparationMutation(
    role: GPUFrameResourceRole,
    transform:
        (
            org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest,
        ) -> org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest,
): CapturedPreparedSurfaceInputs {
    var mutations = 0
    val updated = framePlan.steps.map { step ->
        if (step !is GPUFrameStep.PrepareResourcesStep) {
            step
        } else {
            GPUFrameStep.PrepareResourcesStep(
                requests = step.requests.map { request ->
                    if (request.role == role) {
                        mutations += 1
                        transform(request)
                    } else {
                        request
                    }
                },
                sourceTaskIds = step.sourceTaskIds,
            )
        }
    }
    require(mutations == 1)
    return copy(framePlan = framePlan.rebuiltForDestinationMutation(steps = updated))
}

private fun org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
    .rebuiltForDestinationMutation(
        role: GPUFrameResourceRole = this.role,
        byteSize: Long = this.byteSize,
    ): org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest =
    org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest(
        resource = resource,
        descriptor = descriptor,
        role = role,
        usages = usages,
        lifetime = lifetime,
        byteSize = byteSize,
        diagnosticLabel = diagnosticLabel,
    )

private fun GPUPreparedResourceSet.rebuiltForDestinationMutation(
    ordinaryResources: List<GPUPreparedResourceEvidence> = this.ordinaryResources,
): GPUPreparedResourceSet {
    require(commandResourceLeases.isEmpty() && commandDiagnostics.isEmpty())
    return GPUPreparedResourceSet(
        ordinaryResources = ordinaryResources,
        outputOwnedReadbacks = outputOwnedReadbacks,
        commandTextureResources = commandTextureResources,
        commandBufferResources = commandBufferResources,
    )
}

private fun CapturedPreparedSurfaceInputs.withCoverageMaskProducerMutation(
    transform: (GPUFrameStep.RenderPassStep) -> GPUFrameStep.RenderPassStep,
): CapturedPreparedSurfaceInputs {
    var mutations = 0
    val updated = framePlan.steps.map { step ->
        if (step is GPUFrameStep.RenderPassStep &&
            step.drawPackets.all { packet ->
                packet.role ==
                    org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole.ClipProducer
            }
        ) {
            mutations += 1
            transform(step)
        } else {
            step
        }
    }
    require(mutations == 1)
    return copy(framePlan = framePlan.rebuiltForDestinationMutation(steps = updated))
}

private fun CapturedPreparedSurfaceInputs.withCoverageMaskProducerAfterDestinationCopy():
    CapturedPreparedSurfaceInputs {
    val producerIndex = framePlan.steps.indexOfFirst { step ->
        step is GPUFrameStep.RenderPassStep &&
            step.drawPackets.all { packet ->
                packet.role ==
                    org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole.ClipProducer
            }
    }
    require(producerIndex >= 0)
    val producer = framePlan.steps[producerIndex]
    val withoutProducer = framePlan.steps.toMutableList().apply { removeAt(producerIndex) }
    val copyIndex = withoutProducer.indexOfFirst { it is GPUFrameStep.CopyDestinationStep }
    require(copyIndex >= 0)
    withoutProducer.add(copyIndex + 1, producer)
    return copy(
        framePlan = framePlan.rebuiltForDestinationMutation(steps = withoutProducer),
    )
}

private fun CapturedPreparedSurfaceInputs.withCoverageMaskOperandOrderMutation():
    CapturedPreparedSurfaceInputs {
    var mutations = 0
    val updated = framePlan.steps.map { step ->
        if (step !is GPUFrameStep.RenderPassStep ||
            step.drawPackets.none { it.blendPlan is GPUBlendPlan.ShaderBlendWithDstRead }
        ) {
            step
        } else {
            val uses = step.resourceUses.toMutableList()
            val maskIndex = uses.indexOfFirst { it.role == GPUFrameResourceRole.ClipMask }
            require(maskIndex >= 0 && maskIndex + 1 < uses.size)
            val next = uses[maskIndex + 1]
            uses[maskIndex + 1] = uses[maskIndex]
            uses[maskIndex] = next
            mutations += 1
            step.rebuiltForDestinationMutation(resourceUses = uses)
        }
    }
    require(mutations == 1)
    return copy(framePlan = framePlan.rebuiltForDestinationMutation(steps = updated))
}

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding
    .rebuiltForDestinationMutation(
        preflightSeal:
            org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal =
            this.preflightSeal,
    ): org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding =
    org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding(
        packetId = packetId,
        atlasResourcePlan = atlasResourcePlan,
        instanceBufferPlan = instanceBufferPlan,
        firstInstance = firstInstance,
        instanceCount = instanceCount,
        materialUniformBufferPlan = materialUniformBufferPlan,
        materialUniformOffsetBytes = materialUniformOffsetBytes,
        materialUniformSizeBytes = materialUniformSizeBytes,
        materialSampledResourcePlans = materialSampledResourcePlans,
        preflightSeal = preflightSeal,
        coverageMaskResource = coverageMaskResource,
        colorGlyphBufferPlanOrNull = colorGlyphBufferPlan,
        colorGlyphBufferSliceOrNull = colorGlyphBufferSlice,
    )

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal
    .rebuiltForDestinationMutation(
        blendPlanIdentity: String = this.blendPlanIdentity,
        clipIdentity: String = this.clipIdentity,
        colorGlyphClip:
            org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedColorGlyphClipPreflightSeal? =
            this.colorGlyphClip,
    ): org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal =
    org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal(
        semanticCanonicalHash = semanticCanonicalHash,
        atlasKey = atlasKey,
        atlasWidth = atlasWidth,
        atlasHeight = atlasHeight,
        atlasRowBytes = atlasRowBytes,
        atlasGeneration = atlasGeneration,
        atlasContentHash = atlasContentHash,
        pageIndex = pageIndex,
        instanceStrideBytes = instanceStrideBytes,
        firstInstance = firstInstance,
        instanceCount = instanceCount,
        instanceBufferByteSize = instanceBufferByteSize,
        instanceBufferContentHash = instanceBufferContentHash,
        materialUniformOffsetBytes = materialUniformOffsetBytes,
        materialUniformSizeBytes = materialUniformSizeBytes,
        materialKey = materialKey,
        materialWgslSourceHash = materialWgslSourceHash,
        materialEntryPoint = materialEntryPoint,
        materialAbiHash = materialAbiHash,
        materialUniformContentHash = materialUniformContentHash,
        materialSampledResourceFacts = materialSampledResourceFacts,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        clipIdentity = clipIdentity,
        blendPlanIdentity = blendPlanIdentity,
        capabilitySnapshotHash = capabilitySnapshotHash,
        textA8Composite = textA8Composite,
        colorGlyphClip = colorGlyphClip,
        packetAuthority = packetAuthority,
    )

private fun org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedColorGlyphBufferPlan
    .rebuiltForMaterializerCanonicalityTest(
        uniformBytes: ByteArray,
    ): org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedColorGlyphBufferPlan =
    org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedColorGlyphBufferPlan(
        planArtifactKey = planArtifactKey,
        vertexBufferRef = vertexBufferRef,
        indexBufferRef = indexBufferRef,
        uniformBufferRef = uniformBufferRef,
        uniformAlignmentBytes = uniformAlignmentBytes,
        vertexByteSize = vertexByteSize,
        indexByteSize = indexByteSize,
        uniformByteSize = uniformBytes.size.toLong(),
        vertexContentHash = vertexContentHash,
        indexContentHash = indexContentHash,
        uniformContentHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(uniformBytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) },
        slices = slices,
        vertexBytes = vertexBytesForUpload(),
        indexBytes = indexBytesForUpload(),
        uniformBytes = uniformBytes,
    )

private fun GPUFramePlan.withReversedPreparedImageBindings(): GPUFramePlan {
    var reversedBindingCount = 0
    val updatedSteps = steps.map { step ->
        if (step !is GPUFrameStep.UploadResourceStep || step.imageResourcePlan == null) {
            step
        } else {
            val resourcePlan = requireNotNull(step.imageResourcePlan)
            reversedBindingCount += resourcePlan.bindingRequests.size
            GPUFrameStep.UploadResourceStep(
                staging = step.staging,
                destination = step.destination,
                layout = step.layout,
                sourceTaskIds = step.sourceTaskIds,
                textureResourcePlan = resourcePlan.copy(
                    bindingRequests = resourcePlan.bindingRequests.reversed(),
                ),
            )
        }
    }
    require(reversedBindingCount >= 2) {
        "The regression fixture must contain at least two image bindings"
    }
    return GPUFramePlan(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        steps = updatedSteps,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        elidedNoOpDraws = elidedNoOpDraws,
        atomicallyRefused = atomicallyRefused,
    )
}

/**
 * Full-frame captured input for one accepted prepared-vertices fixture.
 *
 * The preflighter refuses vertices semantics before encoder planning, so the encoder plan,
 * resources, and generation seal are derived here from the same sealed frame authority.
 */
internal fun verticesCapturedPreparedSurfaceInputs(): CapturedPreparedSurfaceInputs {
    val fixture = verticesPreflightFixture()
    val framePlan = fixture.framePlan
    val context = fixture.context
    val sceneTarget = framePlan.steps
        .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        .single { request -> request.role == GPUFrameResourceRole.SceneTarget }
        .resource as GPUFrameTargetRef
    val generationSeal = GPUPreparedGenerationSeal(
        deviceGeneration = context.deviceGeneration,
        targetGeneration = context.targetGeneration,
        resourceGenerations = context.resourceGenerations,
        capabilitySealHash = framePlan.capabilitySeal.sealHash,
    )
    val preparations = framePlan.steps
        .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
    val resources = GPUPreparedResourceSet(
        ordinaryResources = preparations
            .filter { request -> request.role != GPUFrameResourceRole.ReadbackStaging }
            .map { request ->
                GPUPreparedResourceEvidence(
                    logicalResource = request.resource,
                    concreteResource = when (request.descriptor) {
                        is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor ->
                            org.graphiks.kanvas.gpu.renderer.resources
                                .GPUPreparedConcreteResourceRef.Buffer(
                                    org.graphiks.kanvas.gpu.renderer.resources
                                        .GPUBufferResourceRef("concrete.${request.resource.value}"),
                                )
                        is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor ->
                            org.graphiks.kanvas.gpu.renderer.resources
                                .GPUPreparedConcreteResourceRef.Texture(
                                    org.graphiks.kanvas.gpu.renderer.resources
                                        .GPUTextureResourceRef("concrete.${request.resource.value}"),
                                )
                        else -> error("Unsupported prepared-vertices test descriptor")
                    },
                    role = request.role,
                    deviceGeneration = context.deviceGeneration,
                    resourceGeneration = requireNotNull(
                        context.resourceGenerations[request.resource],
                    ),
                )
            },
        outputOwnedReadbacks = emptyList(),
    )
    val encoderPlan = GPUCommandEncoderPlan.ordered(
        planId = "frame.${framePlan.frameId.value}",
        contextIdentity = sceneTarget.value,
        deviceGeneration = context.deviceGeneration,
        targetGeneration = context.targetGeneration,
        scopes = verticesEncoderScopes(framePlan, context, generationSeal),
    )
    return CapturedPreparedSurfaceInputs(
        framePlan = framePlan,
        encoderPlan = encoderPlan,
        resources = resources,
        shaderContract = assertIs<GPUPreparedImageShaderValidationResult.Ready>(
            validatePreparedImageShader(GPU_PREPARED_IMAGE_WGSL),
        ).shaderContract,
        generationSeal = generationSeal,
    )
}

private fun verticesEncoderScopes(
    framePlan: GPUFramePlan,
    context: GPUFramePreflightContext,
    generationSeal: GPUPreparedGenerationSeal,
): List<GPUCommandEncoderScopePlan> = framePlan.steps.mapIndexedNotNull { index, step ->
    when (step) {
        is GPUFrameStep.UploadResourceStep -> {
            val labels = verticesResourceLabels(step, generationSeal)
            GPUCommandEncoderScopePlan(
                sourceStepIndex = index,
                operationKind = GPUEncoderOperationKind.Upload,
                scopeLabel = "step.$index",
                sourceTaskIds = step.sourceTaskIds,
                facadeOperationClasses = listOf("writeBufferOrCopyBuffer"),
                targetGeneration = generationSeal.targetGeneration,
                resourceGenerationLabels = labels,
            ).attachNativeOperandKeys(
                listOf(
                    GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.UploadSource,
                        GPUPreparedNativeOperandKind.Buffer,
                        gpuPreparedNativeBindingKey(labels[0]),
                    ),
                    GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.UploadDestination,
                        GPUPreparedNativeOperandKind.Buffer,
                        gpuPreparedNativeBindingKey(labels[1]),
                    ),
                ),
            )
        }
        is GPUFrameStep.RenderPassStep -> verticesRenderScope(
            index,
            step,
            framePlan,
            generationSeal,
        )
        else -> null
    }
}

private fun verticesResourceLabels(
    step: GPUFrameStep,
    generationSeal: GPUPreparedGenerationSeal,
): List<String> {
    val refs = when (step) {
        is GPUFrameStep.UploadResourceStep -> listOf(step.staging, step.destination)
        is GPUFrameStep.RenderPassStep ->
            listOf(step.target) + step.resourceUses.map { use -> use.resource }
        else -> emptyList()
    }
    return refs.map { ref ->
        "${ref::class.simpleName}:${ref.value}@" +
            requireNotNull(generationSeal.resourceGenerations[ref]) {
                "Prepared-vertices test scope lost generation evidence for ${ref.value}"
            }
    }
}

private fun verticesRenderScope(
    index: Int,
    render: GPUFrameStep.RenderPassStep,
    framePlan: GPUFramePlan,
    generationSeal: GPUPreparedGenerationSeal,
): GPUCommandEncoderScopePlan {
    val labels = verticesResourceLabels(render, generationSeal)
    val bridge = verticesRenderBridge(render, generationSeal)
    val passPlan = org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchPlan(
        streamId = "frame.${framePlan.frameId.value}.step.$index",
        passId = "frame.${framePlan.frameId.value}.render.$index",
        batches = render.batches.map { batch ->
            org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatch(
                batchId = batch.batchId,
                packets = batch.packets,
                kind = batch.kind,
                targetStateHash = batch.packets.first().targetStateHash,
                queueGuard = org.graphiks.kanvas.gpu.renderer.passes
                    .GPUPassBatchQueueGuard(emptyList(), emptyList()),
            )
        },
        cuts = emptyList(),
        diagnostics = emptyList(),
        inputPacketCount = render.drawPackets.size,
    )
    val stream = GPUPassCommandStream.fromBatchPlan(
        streamId = "frame.${framePlan.frameId.value}.commands.$index",
        batchPlan = passPlan,
        loadStoreLabel = render.loadStore.dumpPreparedSurfaceTestLabel(),
        operandBridge = bridge,
    )
    return GPUCommandEncoderScopePlan(
        sourceStepIndex = index,
        operationKind = GPUEncoderOperationKind.Render,
        scopeLabel = "step.$index",
        sourceTaskIds = render.sourceTaskIds,
        sourcePacketIds = render.drawPackets.map { packet -> packet.packetId },
        facadeOperationClasses = stream.commandLabels,
        targetGeneration = generationSeal.targetGeneration,
        resourceGenerationLabels = labels,
        passCommandStream = stream,
        corePrimitiveDirectNativeRouteSeal = GPUCorePrimitiveDirectNativeRouteSeal.Empty,
        targetResource = render.target,
    ).attachNativeOperandKeys(
        verticesRenderOperandKeys(render, labels, bridge),
    )
}

private fun org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan.dumpPreparedSurfaceTestLabel(): String =
    "$loadOp:${storePlan.name}:${clearColorLabel ?: "none"}"

private fun verticesRenderOperandKeys(
    render: GPUFrameStep.RenderPassStep,
    labels: List<String>,
    bridge: List<org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge>,
): List<GPUPreparedNativeOperandKey> = buildList {
    add(
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.RenderColorTarget,
            GPUPreparedNativeOperandKind.TextureView,
            gpuPreparedNativeBindingKey(labels.first()),
        ),
    )
    bridge.forEach { entry ->
        val role = when (entry.operand.kind) {
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
                .RenderPipeline -> GPUPreparedNativeOperandRole.RenderPipeline
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
                .BindGroup -> GPUPreparedNativeOperandRole.RenderBindGroup
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
                .VertexBuffer -> GPUPreparedNativeOperandRole.RenderVertexBuffer
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
                .IndexBuffer -> GPUPreparedNativeOperandRole.RenderIndexBuffer
            else -> error("Unsupported prepared-vertices test bridge kind")
        }
        val kind = when (entry.operand.kind) {
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
                .RenderPipeline -> GPUPreparedNativeOperandKind.RenderPipeline
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
                .BindGroup -> GPUPreparedNativeOperandKind.BindGroup
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
                .VertexBuffer,
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
                .IndexBuffer,
            -> GPUPreparedNativeOperandKind.Buffer
            else -> error("Unsupported prepared-vertices test bridge kind")
        }
        add(
            GPUPreparedNativeOperandKey(
                role,
                kind,
                gpuPreparedNativeBindingKey(
                    "${entry.commandLabel}:${entry.operand.label}",
                ),
            ),
        )
    }
}

private fun verticesRenderBridge(
    render: GPUFrameStep.RenderPassStep,
    generationSeal: GPUPreparedGenerationSeal,
): List<org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge> =
    render.drawPackets.map { packet ->
        val artifact = (packet.semanticPayload as GPUDrawSemanticPayload.Vertices).artifact
        fun operand(
            label: String,
            kind: org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind,
        ) = org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference(
            label = label,
            kind = kind,
            descriptorHash = "vertices.${kind.name}.${packet.packetId.value}",
            deviceGeneration = generationSeal.deviceGeneration.value,
            ownerScope = "PayloadOwnedCompletion",
            usageLabels = listOf("copy_dst"),
            invalidationPolicy = "frame-local",
        )
        listOf(
            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                packet.packetId,
                "setRenderPipeline",
                operand("vertices.pipeline.${packet.packetId.value}",
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUMaterializedCommandOperandKind.RenderPipeline),
            ),
            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                packet.packetId,
                "setBindGroup",
                operand("vertices.draw-group.${packet.packetId.value}",
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUMaterializedCommandOperandKind.BindGroup),
            ),
            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                packet.packetId,
                "setBindGroup",
                operand("vertices.material-group.${packet.packetId.value}",
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUMaterializedCommandOperandKind.BindGroup),
            ),
            org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                packet.packetId,
                "setVertexBuffer",
                operand("vertices.vertex.${artifact.key}",
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUMaterializedCommandOperandKind.VertexBuffer),
            ),
        ) + if (artifact.indexCount != null) {
            listOf(
                org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                    packet.packetId,
                    "setIndexBuffer",
                    operand("vertices.index.${artifact.key}",
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.IndexBuffer),
                ),
            )
        } else {
            emptyList()
        }
    }.flatten()

private class VerticesPayloadFlowFixture(
    val native: GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy,
    val target: GPUWgpu4kPreparedSceneTarget,
    val targetLifecycle: GPUWgpu4kPreparedSceneTargetLifecycle,
    val setup: GPUPreparedSceneSetupTransaction,
    val plan: GPUPreparedVerticesRenderRunPlan,
    val generationSeal: GPUPreparedGenerationSeal,
    val frameId: GPUFrameID,
    val contextIdentity: String,
    val encoderPlan: GPUCommandEncoderPlan,
) {
    val targetViewOperand: GPUPreparedNativeTextureViewOperand
        get() = GPUPreparedNativeTextureViewOperand(
            target.view,
            generationSeal.deviceGeneration,
            GPUPreparedNativeOperandOwnership.Borrowed,
        )

    fun close() {
        runCatching { target.close() }
    }
}

private fun verticesPayloadFlowFixture(): VerticesPayloadFlowFixture {
    val preflightFixture = verticesPreflightFixture(indexed = true, indexFormat = "uint16")
    val framePlan = preflightFixture.framePlan
    val context = preflightFixture.context
    val generationSeal = GPUPreparedGenerationSeal(
        deviceGeneration = context.deviceGeneration,
        targetGeneration = context.targetGeneration,
        resourceGenerations = context.resourceGenerations,
        capabilitySealHash = framePlan.capabilitySeal.sealHash,
    )
    val sceneTarget = framePlan.steps
        .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        .single { request -> request.role == GPUFrameResourceRole.SceneTarget }
        .resource as GPUFrameTargetRef
    val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
    val targetLifecycle = GPUWgpu4kPreparedSceneTargetLifecycle()
    val setup = GPUPreparedSceneSetupTransaction()
    val target = GPUWgpu4kPreparedSceneTarget.create(
        device = native.device,
        width = 16,
        height = 16,
        format = GPUTextureFormat.RGBA8UnormSrgb,
        deviceGeneration = context.deviceGeneration,
        targetGeneration = context.targetGeneration,
        lifecycle = targetLifecycle,
        setupTransaction = setup,
    )
    setup.commit()
    val plan = preparedVerticesRenderRunTestPlan(preflightFixture)
    val renderIndex = framePlan.steps.indexOfFirst { it is GPUFrameStep.RenderPassStep }
    val render = framePlan.steps[renderIndex] as GPUFrameStep.RenderPassStep
    val encoderPlan = GPUCommandEncoderPlan.ordered(
        planId = "frame.${framePlan.frameId.value}",
        contextIdentity = sceneTarget.value,
        deviceGeneration = context.deviceGeneration,
        targetGeneration = context.targetGeneration,
        scopes = listOf(verticesRenderScope(renderIndex, render, framePlan, generationSeal)),
    )
    return VerticesPayloadFlowFixture(
        native = native,
        target = target,
        targetLifecycle = targetLifecycle,
        setup = setup,
        plan = plan,
        generationSeal = generationSeal,
        frameId = framePlan.frameId,
        contextIdentity = sceneTarget.value,
        encoderPlan = encoderPlan,
    )
}
