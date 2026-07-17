package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchAdjacency
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleLoadTransition
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleStoreAction
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadGatherPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUComputeDispatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUCompositeProvenanceToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotConsumerRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUTargetTransitionKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationSession
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUPhysicalPoolMaintenanceDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUPhysicalPoolRollbackSummary
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingRollbackResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GPUFramePreflighterTest {
    @Test
    fun `solid semantic payload refuses every non solid route before any preflight side effect`() {
        listOf(
            "linear.gradient.fill",
            "rrect.fill.coverage",
            "rect.fill.mask_blur",
            "path.fill.coverage_mask",
        ).forEach { route ->
            assertSolidRefusalBeforeSideEffects(
                expectedCode = "invalid.preflight.solid_semantic_route",
                payload = solidSemantic(renderStepIdentity = route),
                packetRenderStepIdentity = route,
            )
        }
    }

    @Test
    fun `executable solid fill rect without semantic payload refuses before operand materialization`() {
        val events = mutableListOf<String>()
        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(framePlan(listOf(prepareScene(), solidRenderStep(null))))

        assertEquals(
            "invalid.preflight.solid_semantic_payload_missing",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertFalse("operands:prepare" in events)
    }

    @Test
    fun `solid semantic command and render step mismatches refuse before operand materialization`() {
        val commandMismatch = preflightSolid(solidSemantic(commandId = 32))
        val stepMismatch = preflightSolid(solidSemantic(renderStepIdentity = "rect.fill.other"))

        assertEquals(
            "invalid.preflight.solid_semantic_command_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(commandMismatch.first).diagnostic.code.value,
        )
        assertEquals(
            "invalid.preflight.solid_semantic_render_step_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(stepMismatch.first).diagnostic.code.value,
        )
        assertFalse("operands:prepare" in commandMismatch.second)
        assertFalse("operands:prepare" in stepMismatch.second)
    }

    @Test
    fun `solid semantic packet slot and internal fingerprint mismatches refuse before operand materialization`() {
        val valid = solidSemantic()
        val slot = valid.payloadRef.uniformSlot!!
        val packetSlotMismatch = preflightSolid(
            payload = valid,
            packetUniformSlot = slot.copy(byteOffset = 256),
        )
        val staleSlot = slot.copy(
            fingerprint = GPUPayloadFingerprint("stale.solid.fingerprint"),
        )
        val fingerprintMismatchPayload = GPUDrawSemanticPayload.SolidRect(
            valid.payloadRef.copy(uniformSlot = staleSlot),
        )
        val fingerprintMismatch = preflightSolid(fingerprintMismatchPayload)

        assertEquals(
            "invalid.preflight.solid_semantic_packet_slot_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(packetSlotMismatch.first).diagnostic.code.value,
        )
        assertEquals(
            "invalid.preflight.solid_semantic_fingerprint_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(fingerprintMismatch.first).diagnostic.code.value,
        )
        assertFalse("operands:prepare" in packetSlotMismatch.second)
        assertFalse("operands:prepare" in fingerprintMismatch.second)
    }

    @Test
    fun `solid semantic malformed ranges bytes padding and values refuse before every preflight side effect`() {
        val valid = solidSemantic()
        val block = valid.payloadRef.uniformBlock!!
        fun payloadWithBlock(changed: org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock) =
            GPUDrawSemanticPayload.SolidRect(valid.payloadRef.copy(uniformBlock = changed))

        val overlappingFields = payloadWithBlock(
            block.copy(fields = block.fields.mapIndexed { index, field ->
                if (index == 1) field.copy(byteOffset = 0) else field
            }),
        )
        val invalidByteCount = payloadWithBlock(block.copy(byteSize = 63))
        val nonZeroPaddingBytes = block.bytes.toMutableList().apply { this[63] = 1 }
        val invalidPadding = payloadWithBlock(block.copy(bytes = nonZeroPaddingBytes))
        val nonFiniteBytes = block.bytes.toMutableList().apply {
            set(0, 0); set(1, 0); set(2, 192); set(3, 127)
        }
        val nonFinite = payloadWithBlock(block.copy(bytes = nonFiniteBytes))
        val outOfRangeColorBytes = block.bytes.toMutableList().apply {
            set(32, 0); set(33, 0); set(34, 0); set(35, 64)
        }
        val outOfRange = payloadWithBlock(block.copy(bytes = outOfRangeColorBytes))

        val cases = listOf(
            "invalid.preflight.solid_semantic_field_ranges" to overlappingFields,
            "invalid.preflight.solid_semantic_byte_count" to invalidByteCount,
            "invalid.preflight.solid_semantic_padding" to invalidPadding,
            "invalid.preflight.solid_semantic_non_finite" to nonFinite,
            "invalid.preflight.solid_semantic_value_range" to outOfRange,
        )
        cases.forEach { (expectedCode, payload) ->
            assertSolidRefusalBeforeSideEffects(expectedCode, payload)
        }
    }

    @Test
    fun `solid semantic ABI matrix refuses before resource native registry and ticket side effects`() {
        val valid = solidSemantic()
        val slot = valid.payloadRef.uniformSlot!!
        val block = valid.payloadRef.uniformBlock!!
        fun payloadWithRef(
            changed: org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef,
        ) = GPUDrawSemanticPayload.SolidRect(changed)
        fun payloadWithBlock(
            changed: org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock,
        ) = payloadWithRef(valid.payloadRef.copy(uniformBlock = changed))

        val forbiddenResourceLayout = payloadWithRef(
            valid.payloadRef.copy(
                resourceSlot = org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot(
                    slotId = org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID("forbidden.resource"),
                    fingerprint = slot.fingerprint,
                    bindingIndex = 0,
                ),
            ),
        )
        val outOfRangeBytes = block.bytes.toMutableList().apply { this[0] = 256 }
        val inconsistentZeroFact = block.fields.mapIndexed { index, field ->
            if (index == 0) field.copy(zeroFilled = !field.zeroFilled) else field
        }
        val negativeRadiusBytes = block.bytes.toMutableList().apply {
            this[16] = 0
            this[17] = 0
            this[18] = 128
            this[19] = 191
        }
        val negativeRadiusFields = block.fields.mapIndexed { index, field ->
            if (index == 4) field.copy(zeroFilled = false) else field
        }

        listOf(
            "invalid.preflight.solid_semantic_uniform_missing" to
                payloadWithRef(valid.payloadRef.copy(uniformSlot = null)),
            "invalid.preflight.solid_semantic_uniform_missing" to
                payloadWithRef(valid.payloadRef.copy(uniformBlock = null)),
            "invalid.preflight.solid_semantic_layout" to forbiddenResourceLayout,
            "invalid.preflight.solid_semantic_layout" to
                payloadWithBlock(block.copy(packingPlanHash = "stale.solid.layout")),
            "invalid.preflight.solid_semantic_layout" to
                payloadWithRef(valid.payloadRef.copy(uniformSlot = slot.copy(byteOffset = 256))),
            "invalid.preflight.solid_semantic_byte_count" to
                payloadWithBlock(block.copy(bytes = outOfRangeBytes)),
            "invalid.preflight.solid_semantic_field_metadata" to
                payloadWithBlock(block.copy(fields = inconsistentZeroFact)),
            "invalid.preflight.solid_semantic_value_range" to
                payloadWithBlock(block.copy(bytes = negativeRadiusBytes, fields = negativeRadiusFields)),
        ).forEach { (expectedCode, payload) ->
            assertSolidRefusalBeforeSideEffects(expectedCode, payload)
        }
    }

    @Test
    fun `preflight preserves the exact solid semantic payload in the typed render operand`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val semantic = solidSemantic()
        val plan = framePlan(listOf(prepareScene(), solidRenderStep(semantic)))
        val materializer = RenderOnlyNativePayloadMaterializer(events)

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                resources = resources,
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
            ).preflight(plan),
        ).frame

        assertSame(semantic, plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single().drawPackets.single().semanticPayload)
        assertSame(semantic, prepared.semanticPlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single().drawPackets.single().semanticPayload)
        assertSame(semantic, requireNotNull(materializer.lastRenderOperand).semanticPayloads.single())
        assertEquals(1, adapter.activePreparedNativeFramePayloadCount)
    }

    @Test
    fun `native render operand omission of solid semantic payload refuses before registry and ticket`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val ownedHandle = CountingDraftHandle()
        val materializer = RenderOnlyNativePayloadMaterializer(
            events,
            omitSemanticPayloads = true,
            ownedHandle = ownedHandle,
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
        ).preflight(framePlan(listOf(prepareScene(), solidRenderStep(solidSemantic()))))

        assertEquals(
            "invalid.preflight.native_render_semantic_payloads",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertFalse("ticket:reserve" in events)
        assertEquals(1, ownedHandle.closeAttempts)
        assertEquals(1, ownedHandle.successfulCloses)
        adapter.close()
        assertEquals(1, ownedHandle.closeAttempts)
    }

    @Test
    fun `native boundary identity mismatch disposes transferred draft before refusal`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val ownedHandle = CountingDraftHandle()
        val materializer = RenderOnlyNativePayloadMaterializer(
            events,
            encoderPlanIdSuffix = ".stale",
            ownedHandle = ownedHandle,
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
        ).preflight(framePlan(listOf(prepareScene(), renderStep())))

        assertEquals(
            "stale.native-frame-payload.identity-mismatch",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(1, ownedHandle.closeAttempts)
        assertEquals(1, ownedHandle.successfulCloses)
        adapter.close()
        assertEquals(1, ownedHandle.closeAttempts)
    }

    @Test
    fun `native identity refusal preserves unique cleanup when one draft identity conflicts`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val conflict = CountingDraftHandle()
        val unique = CountingDraftHandle(closeFailuresRemaining = 1)
        assertTrue(adapter.quarantinePreparedNativeFrameDraft(ownedDraft(70, conflict)))
        val materializer = RenderOnlyNativePayloadMaterializer(
            events,
            encoderPlanIdSuffix = ".stale",
            ownedHandle = conflict,
            additionalOwnedHandles = listOf(unique),
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
        ).preflight(framePlan(listOf(prepareScene(), renderStep())))

        assertEquals(
            "stale.native-frame-payload.identity-mismatch",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, conflict.closeAttempts)
        assertEquals(1, unique.closeAttempts)
        assertEquals(2, adapter.quarantinedPreparedNativeFramePayloadCount)
        adapter.close()
        assertEquals(1, conflict.successfulCloses)
        assertEquals(2, unique.closeAttempts)
        assertEquals(1, unique.successfulCloses)
    }

    @Test
    fun `native semantic refusal preserves unique cleanup when one draft identity conflicts`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val conflict = CountingDraftHandle()
        val unique = CountingDraftHandle(closeFailuresRemaining = 1)
        assertTrue(adapter.quarantinePreparedNativeFrameDraft(ownedDraft(71, conflict)))
        val materializer = RenderOnlyNativePayloadMaterializer(
            events,
            omitSemanticPayloads = true,
            ownedHandle = conflict,
            additionalOwnedHandles = listOf(unique),
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
        ).preflight(framePlan(listOf(prepareScene(), solidRenderStep(solidSemantic()))))

        assertEquals(
            "invalid.preflight.native_render_semantic_payloads",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, conflict.closeAttempts)
        assertEquals(1, unique.closeAttempts)
        assertEquals(2, adapter.quarantinedPreparedNativeFramePayloadCount)
        adapter.close()
        assertEquals(1, conflict.successfulCloses)
        assertEquals(2, unique.closeAttempts)
        assertEquals(1, unique.successfulCloses)
    }

    @Test
    fun `native render payload refuses draw without pipeline and ambiguous clear state`() {
        val generation = GPUDeviceGenerationID(11)
        val target = GPUPreparedNativeTextureViewOperand(fakeNative<GPUTextureView>("target"), generation)

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 1,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(6)),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeRenderPassConfig(
                colorTarget = target,
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = null,
            )
        }
    }

    @Test
    fun `preflight registers one native payload only for registry backed resources`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val plan = framePlan(listOf(prepareScene(), renderStep()))
        val materializer = RenderOnlyNativePayloadMaterializer(events)
        val boundary = adapter.bindNativeFrameBoundary(resources, materializer)

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                resources = resources,
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                nativeBoundary = boundary,
            ).preflight(plan),
        ).frame

        assertTrue(prepared.rollback.hasNativePayload)
        assertEquals(1, adapter.activePreparedNativeFramePayloadCount)
        assertTrue(events.indexOf("native-payload:materialize") < events.indexOf("ticket:reserve"))
    }

    @Test
    fun `evidence only resources cannot construct native payload boundary`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider()

        assertFailsWith<IllegalArgumentException> {
            adapter.bindNativeFrameBoundary(resources, RenderOnlyNativePayloadMaterializer(events))
        }
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
    }

    @Test
    fun `native boundary refuses a different resource provider even with the same adapter`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val boundaryProvider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val otherProvider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val boundary = adapter.bindNativeFrameBoundary(
            boundaryProvider,
            RenderOnlyNativePayloadMaterializer(events),
        )

        val result = preflighter(
            resources = otherProvider,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = boundary,
        ).preflight(framePlan(listOf(prepareScene(), renderStep())))

        assertEquals(
            "invalid.preflight.native_payload_provider_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `late surface bind refusal rolls back surface ticket native payload then resources`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val boundary = adapter.bindNativeFrameBoundary(
            resources,
            RefusingSurfaceNativePayloadMaterializer(events),
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = boundary,
        ).preflight(framePlan(listOf(acquire(), surfaceBlit(), present())))

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals("unsupported.native-frame-payload.surface-bind-refused", refused.diagnostic.code.value)
        assertEquals(
            listOf("surface", "completion-ticket", "native-payload", "resources"),
            requireNotNull(refused.rollbackResult).releaseOrder.map { it.substringBefore(':') },
        )
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertTrue(events.indexOf("native-payload:materialize") < events.indexOf("ticket:reserve"))
        assertTrue(events.indexOf("ticket:reserve") < events.indexOf("surface:acquire:surface.main"))
        assertTrue(events.indexOf("surface:acquire:surface.main") < events.indexOf("native-payload:bind"))
    }

    @Test
    fun `mixed frame is prepared transactionally and encoder scopes cover encodable steps once`() {
        val events = mutableListOf<String>()
        val resources = RecordingResourceProvider(events)
        val completion = RecordingCompletionProvider(events)
        val surface = RecordingSurfaceProvider(events)
        val plan = mixedPlan()

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(resources, completion, surface).preflight(plan),
        ).frame

        assertEquals(
            listOf(
                "resource:prepare:scene",
                "resource:prepare:upload",
                "resource:prepare:copy",
                "resource:prepare:readback",
                "operands:prepare",
                "ticket:reserve",
                "surface:acquire:surface.main",
            ),
            events,
        )
        assertEquals(0, resources.encoderCreateCount)
        assertEquals(0, resources.submitCount)
        assertEquals(
            listOf(1, 2, 3, 5, 6, 8),
            prepared.encoderPlan.scopes.map { scope -> scope.sourceStepIndex },
        )
        assertEquals(
            listOf(
                GPUEncoderOperationKind.Render,
                GPUEncoderOperationKind.Upload,
                GPUEncoderOperationKind.Copy,
                GPUEncoderOperationKind.Compute,
                GPUEncoderOperationKind.Readback,
                GPUEncoderOperationKind.SurfaceBlit,
            ),
            prepared.encoderPlan.scopes.map { scope -> scope.operationKind },
        )
        assertEquals(plan.steps.indices.toList(), prepared.stepPartition.map { evidence -> evidence.sourceStepIndex })
        assertEquals(listOf(7, 9), prepared.hostActions.map { action -> action.sourceStepIndex })
        assertEquals(listOf(4), prepared.dependencyEvidence.map { evidence -> evidence.sourceStepIndex })
        assertEquals(listOf("beginRenderPass", "setRenderPipeline", "setBindGroup", "draw", "endRenderPass"),
            prepared.encoderPlan.scopes.first().facadeOperationClasses)
    }

    @Test
    fun `two render batches keep one begin and one end around ordered packet commands`() {
        val events = mutableListOf<String>()
        val first = packet("packet.a", 1, passId = "pass.source.a")
        val second = packet("packet.b", 2, passId = "pass.source.b")
        val render = GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
            samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(first, second),
            sourceTaskIds = listOf(GPUTaskID("task.render.a"), GPUTaskID("task.render.b")),
            batches = listOf(
                batch("batch.a", first, "task.render.a"),
                batch("batch.b", second, "task.render.b"),
            ),
        )
        val plan = framePlan(listOf(prepareScene(), render))

        val scope = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(plan),
        ).frame.encoderPlan.scopes.single()

        assertEquals(1, scope.facadeOperationClasses.count { it == "beginRenderPass" })
        assertEquals(1, scope.facadeOperationClasses.count { it == "endRenderPass" })
        assertEquals(2, scope.facadeOperationClasses.count { it == "draw" })
        assertEquals(listOf("packet.a", "packet.b"), scope.sourcePacketIds.map { it.value })
    }

    @Test
    fun `copy as draw destination consumer with MSAA refuses exactness before side effects`() {
        val events = mutableListOf<String>()
        val scene = GPUFrameTargetRef("target.scene")
        val source = GPUFrameTextureRef("texture.copy-as-draw.source")
        val snapshot = GPUFrameTextureRef("texture.copy-as-draw.snapshot")
        val packet = packet("packet.msaa.copy-as-draw", 41)
        val taskId = GPUTaskID("task.render.msaa.copy-as-draw")
        val samplePlan = GPUSamplePlan.MultisampleFrame(4)
        val key = msaaContinuationKey(samplePlan)
        val sourceKey = GPUDestinationSnapshotGroupKey(
            target = GPUTargetIdentity("target.scene"),
            targetGeneration = 1,
            deviceGeneration = GPUDeviceGenerationID(7),
            format = GPUColorFormat("rgba8unorm"),
            colorInterpretation = GPUColorInterpretation("srgb-premul"),
            sampleContinuation = key,
            sourceIntermediate = GPUIntermediateIdentity("intermediate.copy-as-draw"),
        )
        val consumer = GPUDestinationSnapshotConsumerRef(
            groupingCommandId = "draw.41",
            renderTaskId = taskId,
            packetId = packet.packetId,
            commandId = GPUDrawCommandID(41),
        )
        val capabilities = capabilities()
        val sealHash = GPUFrameCapabilitySeal.capture(
            GPUFrameID(7),
            GPUDeviceGenerationID(7),
            capabilities,
        ).sealHash
        val render = GPUFrameStep.RenderPassStep(
            target = scene,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = samplePlan,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(taskId),
            batches = listOf(batch("batch.msaa.copy-as-draw", packet, taskId.value)),
            sampleContinuation = GPUSampleContinuationRequest(
                key,
                GPUSampleLoadTransition.FreshClear,
                GPUSampleStoreAction.Store,
                GPUSampleResolveAction.ResolveCanonical,
            ),
        )
        val result = preflighter(
            RecordingResourceProvider(events),
            RecordingCompletionProvider(events),
            RecordingSurfaceProvider(events),
            context = GPUFramePreflightContext(
                targetId = scene.value,
                deviceGeneration = GPUDeviceGenerationID(7),
                targetGeneration = 1,
                resourceGenerations = listOf<GPUFrameResourceRef>(scene, source, snapshot).associateWith { 1L },
            ),
        ).preflight(
            framePlan(
                listOf(
                    GPUFrameStep.PrepareResourcesStep(
                        requests = listOf(
                            GPUResourcePreparationRequest(
                                scene,
                                GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                                GPUFrameResourceRole.SceneTarget,
                                setOf(GPUFrameResourceUsage.RenderAttachment),
                                GPUFrameResourceLifetime.FrameLocal,
                                64,
                                "scene.msaa",
                            ),
                            GPUResourcePreparationRequest(
                                source,
                                GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                                GPUFrameResourceRole.DestinationSnapshot,
                                setOf(GPUFrameResourceUsage.TextureBinding),
                                GPUFrameResourceLifetime.FrameLocal,
                                64,
                                "copy-as-draw.source",
                            ),
                            GPUResourcePreparationRequest(
                                snapshot,
                                GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                                GPUFrameResourceRole.DestinationSnapshot,
                                setOf(
                                    GPUFrameResourceUsage.RenderAttachment,
                                    GPUFrameResourceUsage.TextureBinding,
                                ),
                                GPUFrameResourceLifetime.FrameLocal,
                                64,
                                "copy-as-draw.snapshot",
                            ),
                        ),
                        sourceTaskIds = listOf(GPUTaskID("task.prepare.copy-as-draw")),
                    ),
                    GPUFrameStep.CopyAsDrawMaterializationStep(
                        source = source,
                        sourceKey = sourceKey,
                        sourceIntermediate = requireNotNull(sourceKey.sourceIntermediate),
                        snapshot = snapshot,
                        logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                        capabilitySealHash = sealHash,
                        consumers = listOf(consumer),
                        sourceTaskIds = listOf(GPUTaskID("task.copy-as-draw")),
                    ),
                    render,
                ),
                capabilities,
            ),
        )

        assertEquals(
            "unsupported.blend.msaa_destination_read_exactness",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `MSAA continuation rejects indirect compute writes and contradictory store plans`() {
        val samplePlan = GPUSamplePlan.MultisampleFrame(4)
        val key = msaaContinuationKey(samplePlan)
        fun render(
            id: String,
            load: String,
            storePlan: GPUStorePlan = GPUStorePlan.Store,
        ): GPUFrameStep.RenderPassStep {
            val packet = packet("packet.$id", if (load == "clear") 51 else 52)
            val taskId = GPUTaskID("task.$id")
            return GPUFrameStep.RenderPassStep(
                target = GPUFrameTargetRef("target.scene"),
                loadStore = GPULoadStorePlan(load, storePlan),
                samplePlan = samplePlan,
                drawPackets = listOf(packet),
                sourceTaskIds = listOf(taskId),
                batches = listOf(batch("batch.$id", packet, taskId.value)),
                sampleContinuation = GPUSampleContinuationRequest(
                    key = key,
                    loadTransition = if (load == "clear") {
                        GPUSampleLoadTransition.FreshClear
                    } else {
                        GPUSampleLoadTransition.RetainedLoad
                    },
                    storeAction = GPUSampleStoreAction.Store,
                    resolveAction = GPUSampleResolveAction.ResolveCanonical,
                ),
            )
        }
        val first = render("render.msaa.first", "clear")
        val second = render("render.msaa.second", "load")
        val scene = GPUFrameTargetRef("target.scene")
        val computeTarget = GPUFrameTargetRef("target.compute.other")
        val prepare = GPUFrameStep.PrepareResourcesStep(
            requests = listOf(
                GPUResourcePreparationRequest(
                    scene,
                    GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                    GPUFrameResourceRole.SceneTarget,
                    setOf(
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceUsage.StorageBinding,
                    ),
                    GPUFrameResourceLifetime.FrameLocal,
                    64,
                    "scene.msaa-compute",
                ),
                GPUResourcePreparationRequest(
                    computeTarget,
                    GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                    GPUFrameResourceRole.FilterTarget,
                    setOf(GPUFrameResourceUsage.StorageBinding),
                    GPUFrameResourceLifetime.FrameLocal,
                    64,
                    "compute.other",
                ),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.prepare.msaa-compute")),
        )
        val indirectWriter = GPUFrameStep.ComputePassStep(
            target = computeTarget,
            resourceUses = listOf(
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                    resource = GPUFrameTargetRef("target.scene"),
                    role = GPUFrameResourceRole.SceneTarget,
                    usage = GPUFrameResourceUsage.StorageBinding,
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    write = true,
                ),
            ),
            dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.writer"), 1, 1, 1)),
            sourceTaskIds = listOf(GPUTaskID("task.compute.writer")),
        )
        val scenarios = listOf(
            listOf(prepare, first, indirectWriter, second) to
                "unsupported.msaa.continuation_canonical_write",
            listOf(prepare, render("render.msaa.discard", "clear", GPUStorePlan.Discard)) to
                "unsupported.msaa.continuation_store_operation",
        )
        scenarios.forEach { (steps, expectedCode) ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = GPUFramePreflightContext(
                    targetId = scene.value,
                    deviceGeneration = GPUDeviceGenerationID(7),
                    targetGeneration = 1,
                    resourceGenerations = mapOf(scene to 1L, computeTarget to 1L),
                ),
            ).preflight(framePlan(steps))

            assertEquals(expectedCode, assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value)
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun `destination copy copy as draw transition and refusal steps keep their exact lanes`() {
        val capabilities = capabilities()
        val capabilitySealHash = GPUFrameCapabilitySeal.capture(
            GPUFrameID(7),
            GPUDeviceGenerationID(7),
            capabilities,
        ).sealHash
        fun snapshotKey(intermediate: GPUIntermediateIdentity?): GPUDestinationSnapshotGroupKey =
            GPUDestinationSnapshotGroupKey(
                target = GPUTargetIdentity("target.scene"),
                targetGeneration = 1,
                deviceGeneration = GPUDeviceGenerationID(7),
                format = GPUColorFormat("rgba8unorm"),
                colorInterpretation = GPUColorInterpretation("srgb-premul"),
                sampleContinuation = GPUSampleContinuationKey(
                    target = GPUTargetIdentity("target.scene"),
                    targetGeneration = 1,
                    deviceGeneration = GPUDeviceGenerationID(7),
                    colorFormat = GPUColorFormat("rgba8unorm"),
                    colorInterpretation = GPUColorInterpretation("srgb-premul"),
                    samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.MultisampleFrame(4),
                    colorAttachment = GPUTargetIdentity("target.scene.msaa"),
                    depthStencilAttachment = null,
                ),
                sourceIntermediate = intermediate,
            )
        val consumer = GPUDestinationSnapshotConsumerRef(
            groupingCommandId = "draw.11",
            renderTaskId = GPUTaskID("task.render.11"),
            packetId = GPUDrawPacketID("packet.11"),
            commandId = GPUDrawCommandID(11),
        )
        val scene = GPUFrameTargetRef("target.scene")
        val copySnapshot = GPUFrameTextureRef("texture.copy.snapshot")
        val drawSource = GPUFrameTextureRef("texture.draw.source")
        val drawSnapshot = GPUFrameTextureRef("texture.draw.snapshot")
        val child = GPUFrameTargetRef("target.child")
        val preparedRefs = listOf<GPUFrameResourceRef>(scene, copySnapshot, drawSource, drawSnapshot, child)
        val preparations = GPUFrameStep.PrepareResourcesStep(
            requests = preparedRefs.map { resource ->
                GPUResourcePreparationRequest(
                    resource = resource,
                    descriptor = GPUFrameTextureDescriptor(
                        GPUPixelBounds(0, 0, 4, 4),
                        GPUColorFormat("rgba8unorm"),
                        1,
                    ),
                    role = when (resource) {
                        scene -> GPUFrameResourceRole.SceneTarget
                        child -> GPUFrameResourceRole.LayerTarget
                        else -> GPUFrameResourceRole.DestinationSnapshot
                    },
                    usages = setOf(
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceUsage.CopySource,
                        GPUFrameResourceUsage.CopyDestination,
                        GPUFrameResourceUsage.TextureBinding,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = 64,
                    diagnosticLabel = resource.value,
                )
            },
            sourceTaskIds = listOf(GPUTaskID("task.prepare")),
        )
        val plan = framePlan(
            listOf(
                preparations,
                GPUFrameStep.CopyDestinationStep(
                    source = scene,
                    sourceKey = snapshotKey(null),
                    snapshot = copySnapshot,
                    logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                    copyLayout = GPUTextureCopyLayout(256, 4),
                    consumers = listOf(consumer),
                    sourceTaskIds = listOf(GPUTaskID("task.copy.destination")),
                ),
                GPUFrameStep.CopyAsDrawMaterializationStep(
                    source = drawSource,
                    sourceKey = snapshotKey(GPUIntermediateIdentity("intermediate.source")),
                    sourceIntermediate = GPUIntermediateIdentity("intermediate.source"),
                    snapshot = drawSnapshot,
                    logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                    capabilitySealHash = capabilitySealHash,
                    consumers = listOf(consumer),
                    sourceTaskIds = listOf(GPUTaskID("task.copy.draw")),
                ),
                GPUFrameStep.TargetTransitionStep(
                    parent = scene,
                    child = child,
                    transitionKind = GPUTargetTransitionKind.EnterChild,
                    sourceTaskIds = listOf(GPUTaskID("task.transition")),
                ),
                GPUFrameStep.RefusedLeafDrawStep(
                    commandId = GPUDrawCommandID(12),
                    diagnostic = preflightDiagnostic("unsupported.test.leaf", "leaf refused"),
                    sourceTaskIds = listOf(GPUTaskID("task.refusal.leaf")),
                ),
                GPUFrameStep.RefusedCompositeCommandStep(
                    commandId = GPUDrawCommandID(13),
                    provenanceTokens = listOf(GPUCompositeProvenanceToken("provenance.13")),
                    diagnostic = preflightDiagnostic("unsupported.test.composite", "composite refused"),
                    sourceTaskIds = listOf(GPUTaskID("task.refusal.composite")),
                ),
            ),
            capabilities,
        )
        val events = mutableListOf<String>()
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = "target.scene",
                    deviceGeneration = GPUDeviceGenerationID(7),
                    targetGeneration = 1,
                    resourceGenerations = preparedRefs.associateWith { 1L },
                ),
                capabilities = capabilities,
                resourceProvider = RecordingResourceProvider(events),
                completionProvider = RecordingCompletionProvider(events),
                surfaceProvider = RecordingSurfaceProvider(events),
            ).preflight(plan),
        ).frame

        assertEquals(listOf(1, 2), prepared.encoderPlan.scopes.map { it.sourceStepIndex })
        assertEquals(
            listOf(GPUEncoderOperationKind.CopyDestination, GPUEncoderOperationKind.CopyAsDraw),
            prepared.encoderPlan.scopes.map { it.operationKind },
        )
        assertEquals(listOf(3), prepared.dependencyEvidence.map { it.sourceStepIndex })
        assertEquals(
            listOf(GPUPreparedStepLane.RefusalEvidence, GPUPreparedStepLane.RefusalEvidence),
            prepared.stepPartition.drop(4).map { it.lane },
        )
        assertEquals(listOf("copyTextureToTexture"), prepared.encoderPlan.scopes[0].facadeOperationClasses)
        assertEquals(
            listOf("beginRenderPass", "copyAsDraw", "endRenderPass"),
            prepared.encoderPlan.scopes[1].facadeOperationClasses,
        )
    }

    @Test
    fun `pipeline or bind group refusal rolls resources back in reverse order without surface or encoder`() {
        for (failure in listOf("pipeline", "bind-group")) {
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(events, commandFailure = failure)
            val result = preflighter(
                resources,
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(prepareScene(), renderStep())))

            val refused = assertIs<GPUFramePreflightResult.Refused>(result)
            assertEquals("unsupported.preflight.$failure", refused.diagnostic.code.value)
            assertEquals("resources:rollback", events.last())
            assertFalse(events.any { it.startsWith("surface:") })
            assertEquals(0, resources.encoderCreateCount)
            assertEquals(0, resources.submitCount)
        }
    }

    @Test
    fun `resource scratch budget and provider exceptions refuse transactionally`() {
        val refusals = listOf(
            "scene" to "unsupported.preflight.resource_refused",
            "copy" to "unsupported.physical_pool.scratch_budget_exceeded",
        )
        refusals.forEach { (label, code) ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events, resourceFailureLabel = label, resourceFailureCode = code),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(if (label == "copy") mixedPlan() else framePlan(listOf(prepareScene(), renderStep())))

            assertEquals(code, assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value)
            assertEquals("resources:rollback", events.last())
            assertFalse(events.any { it.startsWith("ticket:") || it.startsWith("surface:") })
        }

        val throwingCases = listOf("resource", "operands", "ticket", "surface")
        throwingCases.forEach { lane ->
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(
                events,
                throwOnResource = lane == "resource",
                throwOnOperands = lane == "operands",
            )
            val completion = RecordingCompletionProvider(events, if (lane == "ticket") "throw" else "reserved")
            val surface = RecordingSurfaceProvider(events, throwOnAcquire = lane == "surface")
            val plan = if (lane == "surface") {
                framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present()))
            } else {
                framePlan(listOf(prepareScene(), renderStep()))
            }

            val refused = assertIs<GPUFramePreflightResult.Refused>(
                preflighter(resources, completion, surface).preflight(plan),
            )
            assertTrue(refused.diagnostic.code.value.startsWith("failed.preflight"))
            assertEquals("resources:rollback", events.last())
        }
    }

    @Test
    fun `submission complete leases and non terminal diagnostics stay in prepared ownership evidence`() {
        val events = mutableListOf<String>()
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events, retainCommandEvidence = true),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(prepareScene(), renderStep()))),
        ).frame

        assertEquals(listOf("lease.render"), prepared.resources.commandResourceLeases.map { it.leaseId })
        assertEquals(listOf("diagnostic.preflight.cache_reuse"), prepared.resources.commandDiagnostics.map { it.code })
        assertTrue(prepared.dumpLines().any { "prepared-command-lease id=lease.render" in it })
        assertTrue(prepared.dumpLines().any { "prepared-command-diagnostic code=diagnostic.preflight.cache_reuse" in it })
        assertEquals(
            listOf("setRenderPipeline.packet.main", "setBindGroup.packet.main"),
            prepared.encoderPlan.scopes.single().passCommandStream!!.materializedOperandLabels,
        )
    }

    @Test
    fun `shared provider isolates same frame attempts and unsafe identities never reach prepared dumps`() {
        val events = mutableListOf<String>()
        val provider = RecordingResourceProvider(events)
        fun prepareOnce(): PreparedGPUFrame = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                provider,
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(mixedPlan()),
        ).frame

        val first = prepareOnce()
        val second = prepareOnce()
        assertNotEquals(
            first.resources.outputOwnedReadbacks.single().stagingLease.ownerScope,
            second.resources.outputOwnedReadbacks.single().stagingLease.ownerScope,
        )

        val unsafe = "w" + "gpuTextureHandle@123"
        val unsafeTarget = GPUFrameTargetRef(unsafe)
        val unsafePlan = framePlan(
            listOf(
                GPUFrameStep.PrepareResourcesStep(
                    requests = listOf(
                        GPUResourcePreparationRequest(
                            resource = unsafeTarget,
                            descriptor = GPUFrameTextureDescriptor(
                                GPUPixelBounds(0, 0, 4, 4),
                                GPUColorFormat("rgba8unorm"),
                                1,
                            ),
                            role = GPUFrameResourceRole.SceneTarget,
                            usages = setOf(GPUFrameResourceUsage.RenderAttachment),
                            lifetime = GPUFrameResourceLifetime.FrameLocal,
                            byteSize = 64,
                            diagnosticLabel = "unsafe",
                        ),
                    ),
                    sourceTaskIds = listOf(GPUTaskID("task.prepare")),
                ),
            ),
        )
        val unsafeEvents = mutableListOf<String>()
        val refused = assertIs<GPUFramePreflightResult.Refused>(
            GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = "target.scene",
                    deviceGeneration = GPUDeviceGenerationID(7),
                    targetGeneration = 1,
                    resourceGenerations = mapOf(unsafeTarget to 1L),
                ),
                capabilities = capabilities(),
                resourceProvider = RecordingResourceProvider(unsafeEvents),
                completionProvider = RecordingCompletionProvider(unsafeEvents),
                surfaceProvider = RecordingSurfaceProvider(unsafeEvents),
            ).preflight(unsafePlan),
        )
        assertEquals("invalid.preflight.dump_unsafe_identity", refused.diagnostic.code.value)
        assertFalse(refused.diagnostic.message.contains(unsafe))
        assertFalse(refused.diagnostic.facts.values.any { unsafe in it })
        assertTrue(unsafeEvents.isEmpty())
        assertFailsWith<IllegalArgumentException> { GPUQueueCompletionTicketID(unsafe) }
        assertFailsWith<IllegalArgumentException> {
            GPUAcquiredSurfaceOutput(GPUSurfaceOutputRef("surface"), GPUDeviceGenerationID(7), 1, unsafe)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedConcreteResourceRef.Texture(GPUTextureResourceRef(unsafe))
        }

        val unsafePacket = packet("packet.unsafe", 9, pipelineKey = unsafe)
        val unsafePipelinePlan = framePlan(
            listOf(
                prepareScene(),
                GPUFrameStep.RenderPassStep(
                    target = GPUFrameTargetRef("target.scene"),
                    loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                    samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
                    drawPackets = listOf(unsafePacket),
                    sourceTaskIds = listOf(GPUTaskID("task.render")),
                    batches = listOf(batch("batch.unsafe", unsafePacket, "task.render")),
                ),
            ),
        )
        val unsafePipelineEvents = mutableListOf<String>()
        val unsafePipelineResult = preflighter(
            RecordingResourceProvider(unsafePipelineEvents),
            RecordingCompletionProvider(unsafePipelineEvents),
            RecordingSurfaceProvider(unsafePipelineEvents),
        ).preflight(unsafePipelinePlan)
        assertEquals(
            "invalid.preflight.dump_unsafe_identity",
            assertIs<GPUFramePreflightResult.Refused>(unsafePipelineResult).diagnostic.code.value,
        )
        assertTrue(unsafePipelineEvents.isEmpty())
    }

    @Test
    fun `missing failed duplicate or mismatched completion proof rolls back before surface acquisition`() {
        val modes = listOf("missing", "failed", "duplicate", "wrong-frame", "wrong-generation")
        for (mode in modes) {
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(events)
            val result = preflighter(
                resources,
                RecordingCompletionProvider(events, mode),
                RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present())))

            val refused = assertIs<GPUFramePreflightResult.Refused>(result)
            assertTrue(refused.diagnostic.code.value.startsWith("unsupported.preflight.completion_ticket"))
            assertEquals("resources:rollback", events.last())
            assertFalse(events.any { it.startsWith("surface:") })
        }
    }

    @Test
    fun `zero resource plan abandons mismatched reserved ticket without accumulation`() {
        val events = mutableListOf<String>()
        val result = preflighter(
            RecordingResourceProvider(events),
            RecordingCompletionProvider(events, "wrong-frame"),
            RecordingSurfaceProvider(events),
        ).preflight(framePlan(emptyList()))

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals("unsupported.preflight.completion_ticket_mismatch", refused.diagnostic.code.value)
        assertEquals(1, events.count { it == "ticket:reserve" })
        assertEquals(1, events.count { it.startsWith("ticket:abandon:") })
        assertEquals("resources:rollback", events.last())
        assertFalse(events.any { it.startsWith("surface:") })
    }

    @Test
    fun `surface statuses are normalized and rollback resources after ticket reservation`() {
        val expectations = mapOf(
            GPUSurfaceAcquisitionStatus.Lost to "reconfigure",
            GPUSurfaceAcquisitionStatus.Outdated to "reconfigure",
            GPUSurfaceAcquisitionStatus.Timeout to "retry",
            GPUSurfaceAcquisitionStatus.OutOfMemory to "terminal",
            GPUSurfaceAcquisitionStatus.DeviceLost to "terminal",
        )
        expectations.forEach { (status, recovery) ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events, status),
            ).preflight(framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present())))

            val refused = assertIs<GPUFramePreflightResult.Refused>(result)
            assertEquals(recovery, refused.diagnostic.facts["recovery"])
            assertEquals(
                listOf(
                    "resource:prepare:scene",
                    "operands:prepare",
                    "ticket:reserve",
                    "surface:acquire:surface.main",
                    "ticket:abandon:ticket.7",
                    "resources:rollback",
                ),
                events,
            )
        }
    }

    @Test
    fun `device target resource and capability drift refuse before unsafe acquisition`() {
        val basePlan = framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present()))
        val cases = listOf(
            preflightContext(deviceGeneration = GPUDeviceGenerationID(8)),
            preflightContext(targetGeneration = 2),
            preflightContext(resourceGeneration = 2),
        )
        for (context in cases) {
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = context,
            ).preflight(basePlan)
            assertIs<GPUFramePreflightResult.Refused>(result)
            assertTrue(events.isEmpty())
        }

        val events = mutableListOf<String>()
        val driftedCapabilities = capabilities(snapshotId = "capabilities.changed")
        val drifted = GPUFramePreflighter(
            context = preflightContext(),
            capabilities = driftedCapabilities,
            resourceProvider = RecordingResourceProvider(events),
            completionProvider = RecordingCompletionProvider(events),
            surfaceProvider = RecordingSurfaceProvider(events),
        ).preflight(basePlan)
        assertIs<GPUFramePreflightResult.Refused>(drifted)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `readback layout failure refuses before any resource ticket or surface acquisition`() {
        val events = mutableListOf<String>()
        val capabilities = capabilities(maxBufferSize = null)
        val result = GPUFramePreflighter(
            context = preflightContext(),
            capabilities = capabilities,
            resourceProvider = RecordingResourceProvider(events),
            completionProvider = RecordingCompletionProvider(events),
            surfaceProvider = RecordingSurfaceProvider(events),
        ).preflight(mixedPlan(capabilities))

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals("unsupported.readback.max_buffer_size_unavailable", refused.diagnostic.code.value)
        assertTrue(events.isEmpty())

        val duplicateEvents = mutableListOf<String>()
        val base = mixedPlan()
        val readback = base.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().single()
        val duplicatePlan = framePlan(
            base.steps + GPUFrameStep.ReadbackCopyStep(
                source = readback.source,
                staging = readback.staging,
                request = readback.request,
                sourceTaskIds = listOf(GPUTaskID("task.readback.duplicate")),
            ),
        )
        val duplicate = preflighter(
            RecordingResourceProvider(duplicateEvents),
            RecordingCompletionProvider(duplicateEvents),
            RecordingSurfaceProvider(duplicateEvents),
        ).preflight(duplicatePlan)
        assertEquals(
            "invalid.preflight.readback_request_id_duplicate",
            assertIs<GPUFramePreflightResult.Refused>(duplicate).diagnostic.code.value,
        )
        assertTrue(duplicateEvents.isEmpty())
    }

    @Test
    fun `readback lease is output owned and prepared rollback is global reverse one shot`() {
        val events = mutableListOf<String>()
        val surface = RecordingSurfaceProvider(events)
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                surface,
            ).preflight(mixedPlan()),
        ).frame

        assertEquals(1, prepared.resources.outputOwnedReadbacks.size)
        assertTrue(prepared.resources.ordinaryResources.none { it.role == GPUFrameResourceRole.ReadbackStaging })
        val beforeHash = prepared.stableHash()
        val first = prepared.rollback.execute()
        val second = prepared.rollback.execute()

        assertTrue(first === second)
        assertEquals(beforeHash, prepared.stableHash())
        assertEquals(1, events.count { it == "surface:release:surface.main" })
        assertEquals(1, events.count { it == "resources:rollback" })
        assertTrue(events.indexOf("surface:release:surface.main") < events.indexOf("resources:rollback"))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (prepared.encoderPlan.scopes as MutableList<GPUCommandEncoderScopePlan>).clear()
        }
    }

    @Test
    fun `surface release failure still rolls resources back and returns stable rollback evidence`() {
        val events = mutableListOf<String>()
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events, throwOnRelease = true),
            ).preflight(framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present()))),
        ).frame

        val result = prepared.rollback.execute()

        assertFalse(result.successful)
        assertEquals("failed.preflight.surface_release", result.diagnostics.single().code.value)
        assertEquals("resources:rollback", events.last())
    }

    @Test
    fun `undeclared resource and mismatched surface chain refuse before acquisition`() {
        val undeclaredEvents = mutableListOf<String>()
        val undeclared = preflighter(
            RecordingResourceProvider(undeclaredEvents),
            RecordingCompletionProvider(undeclaredEvents),
            RecordingSurfaceProvider(undeclaredEvents),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    GPUFrameStep.CopyResourceStep(
                        source = GPUFrameTargetRef("target.scene"),
                        destination = GPUFrameTextureRef("texture.undeclared"),
                        regions = listOf(GPUResourceCopyRegion(0, 0, null, 16)),
                        sourceTaskIds = listOf(GPUTaskID("task.copy")),
                    ),
                ),
            ),
        )
        assertEquals(
            "invalid.preflight.resource_undeclared",
            assertIs<GPUFramePreflightResult.Refused>(undeclared).diagnostic.code.value,
        )
        assertTrue(undeclaredEvents.isEmpty())

        val outputEvents = mutableListOf<String>()
        val mismatched = preflighter(
            RecordingResourceProvider(outputEvents),
            RecordingCompletionProvider(outputEvents),
            RecordingSurfaceProvider(outputEvents),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    renderStep(),
                    acquire(),
                    GPUFrameStep.SurfaceBlitRenderPassStep(
                        scene = GPUFrameTargetRef("target.scene"),
                        output = GPUSurfaceOutputRef("surface.other"),
                        sourceTaskIds = listOf(GPUTaskID("task.output")),
                    ),
                    present(),
                ),
            ),
        )
        assertEquals(
            "invalid.preflight.surface_output_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(mismatched).diagnostic.code.value,
        )
        assertTrue(outputEvents.isEmpty())
    }

    private fun preflighter(
        resources: GPUFrameResourcePreflightProvider,
        completion: GPUQueueCompletionProvider,
        surface: GPUSurfaceOutputProvider,
        context: GPUFramePreflightContext = preflightContext(),
        nativeBoundary: GPUPreparedNativeFrameBoundary? = null,
    ): GPUFramePreflighter = GPUFramePreflighter(
        context = context,
        capabilities = capabilities(),
        resourceProvider = resources,
        completionProvider = completion,
        surfaceProvider = surface,
        nativeBoundary = nativeBoundary,
    )

    private class RecordingResourceProvider(
        private val events: MutableList<String>,
        private val commandFailure: String? = null,
        private val resourceFailureLabel: String? = null,
        private val resourceFailureCode: String = "unsupported.preflight.resource_refused",
        private val throwOnResource: Boolean = false,
        private val throwOnOperands: Boolean = false,
        private val retainCommandEvidence: Boolean = false,
    ) : GPUFrameResourcePreflightProvider {
        var encoderCreateCount = 0
        var submitCount = 0
        private var sessionOrdinal = 0L

        override fun beginFramePreparation(
            frameId: Long,
            deviceGeneration: GPUDeviceGenerationID,
        ): GPUFrameResourcePreparationSession = GPUFrameResourcePreparationSession(
            "frame-preflight:$frameId:device:${deviceGeneration.value}:attempt:${++sessionOrdinal}",
        )

        override fun prepareFrameResource(
            input: GPUFrameResourcePreparationInput,
        ): GPUFrameResourcePreparationDecision {
            events += "resource:prepare:${input.preparation.diagnosticLabel}"
            if (throwOnResource) error("resource provider failed")
            if (input.preparation.diagnosticLabel == resourceFailureLabel) {
                return GPUFrameResourcePreparationDecision.Refused(
                    preflightDiagnostic(resourceFailureCode, "resource preparation refused"),
                )
            }
            return GPUFrameResourcePreparationDecision.Prepared(
                logicalResource = input.preparation.resource,
                concreteResource = when (input.preparation.resource) {
                    is GPUFrameBufferRef -> GPUPreparedConcreteResourceRef.Buffer(
                        GPUBufferResourceRef("prepared:${input.preparation.resource.value}"),
                    )
                    else -> GPUPreparedConcreteResourceRef.Texture(
                        GPUTextureResourceRef("prepared:${input.preparation.resource.value}"),
                    )
                },
                role = input.preparation.role,
                deviceGeneration = input.deviceGeneration,
                resourceGeneration = input.resourceGeneration,
                outputOwnedReadbackLease = if (input.preparation.role == GPUFrameResourceRole.ReadbackStaging) {
                    GPUReadbackStagingLease(
                        reservationId = "readback.test",
                        ownerScope = input.ownerScope,
                        deviceGeneration = input.deviceGeneration,
                        resourceRef = GPUBufferResourceRef("prepared:${input.preparation.resource.value}"),
                        reservationOrdinal = 1,
                        acquisitionToken = 1,
                        logicalMinimumBytes = input.readbackStagingDescriptor!!.minimumBufferBytes,
                        backingBufferBytes = (input.preparation.descriptor as GPUFrameBufferDescriptor).byteSize,
                        usages = input.preparation.usages,
                    )
                } else null,
            )
        }

        override fun materializeCommandOperands(
            request: GPUCommandOperandMaterializationRequest,
            context: GPUTargetPreparationContext,
        ): GPUResourceMaterializationDecision {
            events += "operands:prepare"
            if (throwOnOperands) error("operand provider failed")
            val failure = commandFailure
            if (failure != null) {
                return GPUResourceMaterializationDecision.Refused(
                    diagnostic = GPUResourceDiagnostic(
                        code = "unsupported.preflight.$failure",
                        resourceLabel = failure,
                        message = "$failure refused",
                        terminal = true,
                    ),
                    targetId = context.targetId,
                )
            }
            val ownerScope = request.operands.firstOrNull()?.ownerScope ?: "frame-preflight:none"
            return GPUResourceMaterializationDecision.Materialized(
                resources = emptyList(),
                diagnostics = if (retainCommandEvidence) {
                    listOf(
                        GPUResourceDiagnostic(
                            code = "diagnostic.preflight.cache_reuse",
                            resourceLabel = "render-cache",
                            message = "Render cache entry was reused.",
                            terminal = false,
                            facts = mapOf("result" to "reuse"),
                        ),
                    )
                } else emptyList(),
                targetId = context.targetId,
                taskIds = request.taskIds,
                resourcePlanLabels = request.resourcePlanLabels,
                operandBridge = request.operands.map { operand ->
                    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandBinding(
                        packetId = operand.packetId,
                        commandLabel = operand.commandLabel,
                        operand = org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference(
                            label = operand.label,
                            kind = operand.kind,
                            descriptorHash = operand.descriptorHash,
                            deviceGeneration = operand.deviceGeneration,
                            ownerScope = operand.ownerScope,
                            usageLabels = operand.requiredUsageLabels.toList(),
                            invalidationPolicy = operand.invalidationPolicy,
                            evidenceFacts = operand.evidenceFacts,
                        ),
                    )
                },
                resourceLeases = if (retainCommandEvidence) {
                    listOf(
                        GPUResourceLease(
                            leaseId = "lease.render",
                            resourceKind = GPUResourceLeaseKind.BindGroup,
                            deviceGeneration = context.deviceGeneration,
                            descriptorHash = "render.bindings",
                            ownerScope = ownerScope,
                            usageLabels = listOf("render"),
                            releasePolicy = "submission-complete",
                            cacheResult = GPUResourceLeaseCacheResult.Create,
                        ),
                    )
                } else emptyList(),
            )
        }

        override fun rollbackFrameResourcesBeforeSubmit(
            ownerScope: String,
        ): GPUPhysicalPoolMaintenanceDecision<GPUPhysicalPoolRollbackSummary> {
            events += "resources:rollback"
            return GPUPhysicalPoolMaintenanceDecision.Applied(
                GPUPhysicalPoolRollbackSummary(
                    scratch = org.graphiks.kanvas.gpu.renderer.resources.GPUScratchRollbackResult(
                        emptyList(),
                        emptyList(),
                    ),
                    readback = GPUReadbackStagingRollbackResult(emptyList(), emptyList()),
                    releaseOrder = emptyList(),
                ),
            )
        }
    }

    private inner class RenderOnlyNativePayloadMaterializer(
        private val events: MutableList<String>,
        private val omitSemanticPayloads: Boolean = false,
        private val encoderPlanIdSuffix: String = "",
        private val ownedHandle: AutoCloseable? = null,
        private val additionalOwnedHandles: List<AutoCloseable> = emptyList(),
    ) : GPUPreparedNativeFramePayloadMaterializer {
        var lastRenderOperand: GPUPreparedNativeScopeOperand.Render? = null
            private set

        override fun materializeReusable(
            framePlan: GPUFramePlan,
            encoderPlan: GPUCommandEncoderPlan,
            resources: GPUPreparedResourceSet,
            generationSeal: GPUPreparedGenerationSeal,
        ): GPUPreparedNativeFramePayloadMaterialization {
            events += "native-payload:materialize"
            val scope = encoderPlan.scopes.single()
            val semanticPayloads = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap { step -> step.drawPackets.mapNotNull(GPUDrawPacket::semanticPayload) }
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = scope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = GPUPreparedNativeTextureViewOperand(
                        fakeNative<GPUTextureView>("target.scene.view"),
                        generationSeal.deviceGeneration,
                    ),
                ),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(
                        GPUPreparedNativeRenderPipelineOperand(
                            fakeNative<GPURenderPipeline>("pipeline.rect"),
                            generationSeal.deviceGeneration,
                        ),
                    ),
                    GPUPreparedNativeRenderCommand.SetBindGroup(
                        index = 0,
                        bindGroup = GPUPreparedNativeBindGroupOperand(
                            fakeNative<GPUBindGroup>("binding.rect"),
                            generationSeal.deviceGeneration,
                        ),
                    ),
                    GPUPreparedNativeRenderCommand.Draw(
                        GPUPreparedNativeDrawCall.Draw(vertexCount = 6),
                    ),
                ),
                semanticPayloads = if (omitSemanticPayloads) emptyList() else semanticPayloads,
            )
            lastRenderOperand = renderOperand
            return GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(
                    GPUPreparedNativeFramePayload(
                        identity = GPUPreparedNativeFrameIdentity(
                            frameId = framePlan.frameId,
                            contextIdentity = encoderPlan.contextIdentity,
                            encoderPlanId = encoderPlan.planId + encoderPlanIdSuffix,
                            deviceGeneration = generationSeal.deviceGeneration,
                            targetGeneration = generationSeal.targetGeneration,
                            scopes = listOf(
                                GPUPreparedNativeScopeKey(
                                    scope.sourceStepIndex,
                                    scope.operationKind,
                                    scope.resourceGenerationLabels,
                                    scope.nativeOperandKeys,
                                ),
                            ),
                        ),
                        scopeOperands = listOf(renderOperand),
                        scopeOperandKeys = listOf(scope.nativeOperandKeys),
                        auxiliaryOwnedHandles = (listOfNotNull(ownedHandle) + additionalOwnedHandles)
                            .map { handle ->
                                GPUPreparedNativeAuxiliaryHandle(
                                    handle,
                                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                                )
                            },
                    ),
                ),
            )
        }

        override fun bindLateSurface(
            draft: GPUPreparedNativeFrameDraft,
            acquiredSurface: GPUAcquiredSurfaceOutput?,
        ): GPUPreparedNativeFrameLateSurfaceBinding =
            GPUPreparedNativeFrameLateSurfaceBinding.NotRequired
    }

    private fun ownedDraft(frame: Long, handle: AutoCloseable) = GPUPreparedNativeFrameDraft(
        GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                frameId = GPUFrameID(frame),
                contextIdentity = "conflict.$frame",
                encoderPlanId = "conflict.$frame",
                deviceGeneration = GPUDeviceGenerationID(11),
                targetGeneration = 7,
                scopes = emptyList(),
            ),
            scopeOperands = emptyList(),
            scopeOperandKeys = emptyList(),
            auxiliaryOwnedHandles = listOf(
                GPUPreparedNativeAuxiliaryHandle(
                    handle,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        ),
    )

    private class CountingDraftHandle(
        private var closeFailuresRemaining: Int = 0,
    ) : AutoCloseable {
        var closeAttempts = 0
            private set
        var successfulCloses = 0
            private set

        override fun close() {
            closeAttempts += 1
            if (closeFailuresRemaining > 0) {
                closeFailuresRemaining -= 1
                error("draft close failed")
            }
            successfulCloses += 1
        }
    }

    private inner class RefusingSurfaceNativePayloadMaterializer(
        private val events: MutableList<String>,
    ) : GPUPreparedNativeFramePayloadMaterializer {
        override fun materializeReusable(
            framePlan: GPUFramePlan,
            encoderPlan: GPUCommandEncoderPlan,
            resources: GPUPreparedResourceSet,
            generationSeal: GPUPreparedGenerationSeal,
        ): GPUPreparedNativeFramePayloadMaterialization {
            events += "native-payload:materialize"
            val scope = encoderPlan.scopes.single()
            return GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(
                    GPUPreparedNativeFramePayload(
                        identity = GPUPreparedNativeFrameIdentity(
                            frameId = framePlan.frameId,
                            contextIdentity = encoderPlan.contextIdentity,
                            encoderPlanId = encoderPlan.planId,
                            deviceGeneration = generationSeal.deviceGeneration,
                            targetGeneration = generationSeal.targetGeneration,
                            scopes = listOf(
                                GPUPreparedNativeScopeKey(
                                    scope.sourceStepIndex,
                                    scope.operationKind,
                                    scope.resourceGenerationLabels,
                                    scope.nativeOperandKeys,
                                ),
                            ),
                        ),
                        scopeOperands = listOf(
                            GPUPreparedNativeScopeOperand.SurfaceBlit(
                                sourceStepIndex = scope.sourceStepIndex,
                                source = GPUPreparedNativeTextureViewOperand(
                                    fakeNative<GPUTextureView>("surface-source"),
                                    generationSeal.deviceGeneration,
                                ),
                                output = GPUSurfaceOutputRef("surface.main"),
                                pipeline = GPUPreparedNativeRenderPipelineOperand(
                                    fakeNative<GPURenderPipeline>("surface-pipeline"),
                                    generationSeal.deviceGeneration,
                                ),
                                bindGroup = GPUPreparedNativeBindGroupOperand(
                                    fakeNative<GPUBindGroup>("surface-bind-group"),
                                    generationSeal.deviceGeneration,
                                ),
                            ),
                        ),
                        scopeOperandKeys = listOf(scope.nativeOperandKeys),
                    ),
                ),
            )
        }

        override fun bindLateSurface(
            draft: GPUPreparedNativeFrameDraft,
            acquiredSurface: GPUAcquiredSurfaceOutput?,
        ): GPUPreparedNativeFrameLateSurfaceBinding {
            events += "native-payload:bind"
            return GPUPreparedNativeFrameLateSurfaceBinding.Refused(
                "unsupported.native-frame-payload.surface-bind-refused",
                "Surface target could not be bound.",
            )
        }
    }

    private inline fun <reified T> fakeNative(label: String): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getLabel" -> label
            "setLabel", "close" -> Unit
            "toString" -> "FakeNative($label)"
            else -> error("Unexpected fake native call: ${method.name}")
        }
    } as T

    private class RecordingCompletionProvider(
        private val events: MutableList<String>,
        private val mode: String = "reserved",
    ) : GPUQueueCompletionProvider {
        override fun abandonReservedTicket(
            ticket: GPUQueueCompletionTicket,
        ): GPUQueueCompletionTicketAbandonResult {
            events += "ticket:abandon:${ticket.ticketId.value}"
            return GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
        }

        override fun reserveTicket(
            request: GPUQueueCompletionTicketRequest,
        ): GPUQueueCompletionTicketReservation {
            events += "ticket:reserve"
            return when (mode) {
                "missing" -> GPUQueueCompletionTicketReservation.Missing
                "failed" -> GPUQueueCompletionTicketReservation.Failed(
                    preflightDiagnostic("unsupported.preflight.completion_ticket_failed", "queue proof unavailable"),
                )
                "duplicate" -> GPUQueueCompletionTicketReservation.Duplicate(GPUQueueCompletionTicketID("ticket.7"))
                "wrong-frame" -> GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(GPUQueueCompletionTicketID("ticket.7"), GPUFrameID(request.frameId.value + 1), request.deviceGeneration),
                )
                "wrong-generation" -> GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(
                        GPUQueueCompletionTicketID("ticket.7"),
                        request.frameId,
                        GPUDeviceGenerationID(request.deviceGeneration.value + 1),
                    ),
                )
                "throw" -> error("ticket provider failed")
                else -> GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(GPUQueueCompletionTicketID("ticket.7"), request.frameId, request.deviceGeneration),
                )
            }
        }
    }

    private class RecordingSurfaceProvider(
        private val events: MutableList<String>,
        private val status: GPUSurfaceAcquisitionStatus? = null,
        private val throwOnRelease: Boolean = false,
        private val throwOnAcquire: Boolean = false,
    ) : GPUSurfaceOutputProvider {
        override fun acquire(
            request: GPUSurfaceAcquisitionRequest,
        ): GPUSurfaceAcquisitionResult {
            events += "surface:acquire:${request.descriptor.output.value}"
            if (throwOnAcquire) error("surface acquire failed")
            return if (status == null) {
                GPUSurfaceAcquisitionResult.Acquired(
                    GPUAcquiredSurfaceOutput(
                        output = request.descriptor.output,
                        deviceGeneration = request.deviceGeneration,
                        targetGeneration = request.descriptor.targetGeneration,
                        evidenceLabel = "surface-output",
                    ),
                )
            } else {
                GPUSurfaceAcquisitionResult.Unavailable(status)
            }
        }

        override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult {
            events += "surface:release:${output.output.value}"
            if (throwOnRelease) error("surface release failed")
            return GPUSurfaceReleaseResult.Released
        }
    }

    private fun mixedPlan(capabilities: GPUCapabilities = capabilities()): GPUFramePlan = framePlan(
        listOf(
            prepareResources(),
            renderStep(),
            GPUFrameStep.UploadResourceStep(
                staging = GPUFrameBufferRef("buffer.upload"),
                destination = GPUFrameTargetRef("target.scene"),
                layout = GPUUploadLayout(0, 256, 1, 64),
                sourceTaskIds = listOf(GPUTaskID("task.upload")),
            ),
            GPUFrameStep.CopyResourceStep(
                source = GPUFrameTargetRef("target.scene"),
                destination = GPUFrameTextureRef("texture.copy"),
                regions = listOf(GPUResourceCopyRegion(0, 0, GPUPixelBounds(0, 0, 4, 4), 64)),
                sourceTaskIds = listOf(GPUTaskID("task.copy")),
            ),
            GPUFrameStep.DependencyBarrierStep(
                orderedUseTokens = listOf(GPUTaskUseToken("use.upload"), GPUTaskUseToken("use.copy")),
                reasonCode = "dependency.copy-after-upload",
                sourceTaskIds = listOf(GPUTaskID("task.barrier")),
            ),
            GPUFrameStep.ComputePassStep(
                target = GPUFrameTargetRef("target.scene"),
                resourceUses = emptyList(),
                dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.key"), 1, 1, 1)),
                sourceTaskIds = listOf(GPUTaskID("task.compute")),
            ),
            GPUFrameStep.ReadbackCopyStep(
                source = GPUFrameTargetRef("target.scene"),
                staging = GPUFrameBufferRef("buffer.readback"),
                request = GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("readback.main"),
                    sourceBounds = GPUPixelBounds(0, 0, 4, 4),
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                ),
                sourceTaskIds = listOf(GPUTaskID("task.readback")),
            ),
            acquire(),
            surfaceBlit(),
            present(),
        ),
        capabilities,
    )

    private fun prepareResources(): GPUFrameStep.PrepareResourcesStep = GPUFrameStep.PrepareResourcesStep(
        requests = listOf(
            GPUResourcePreparationRequest(
                resource = GPUFrameTargetRef("target.scene"),
                descriptor = GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                role = GPUFrameResourceRole.SceneTarget,
                usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 64,
                diagnosticLabel = "scene",
            ),
            GPUResourcePreparationRequest(
                resource = GPUFrameBufferRef("buffer.upload"),
                descriptor = GPUFrameBufferDescriptor(64, 4),
                role = GPUFrameResourceRole.UploadStaging,
                usages = setOf(GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 64,
                diagnosticLabel = "upload",
            ),
            GPUResourcePreparationRequest(
                resource = GPUFrameTextureRef("texture.copy"),
                descriptor = GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                role = GPUFrameResourceRole.DestinationSnapshot,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 64,
                diagnosticLabel = "copy",
            ),
            GPUResourcePreparationRequest(
                resource = GPUFrameBufferRef("buffer.readback"),
                descriptor = GPUFrameBufferDescriptor(1024, 4),
                role = GPUFrameResourceRole.ReadbackStaging,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 1024,
                diagnosticLabel = "readback",
            ),
        ),
        sourceTaskIds = listOf(GPUTaskID("task.prepare")),
    )

    private fun prepareScene(): GPUFrameStep.PrepareResourcesStep = GPUFrameStep.PrepareResourcesStep(
        requests = listOf(
            GPUResourcePreparationRequest(
                resource = GPUFrameTargetRef("target.scene"),
                descriptor = GPUFrameTextureDescriptor(
                    GPUPixelBounds(0, 0, 4, 4),
                    GPUColorFormat("rgba8unorm"),
                    1,
                ),
                role = GPUFrameResourceRole.SceneTarget,
                usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 64,
                diagnosticLabel = "scene",
            ),
        ),
        sourceTaskIds = listOf(GPUTaskID("task.prepare")),
    )

    private fun renderStep(): GPUFrameStep.RenderPassStep {
        val packet = packet("packet.main", 1)
        return GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID("task.render")),
            batches = listOf(batch("batch.main", packet, "task.render")),
        )
    }

    private fun solidRenderStep(
        payload: GPUDrawSemanticPayload?,
        commandId: Int = 31,
        packetUniformSlot: GPUUniformPayloadSlot? = payload?.payloadRef?.uniformSlot,
        renderStepIdentity: String = "rect.fill.coverage",
    ): GPUFrameStep.RenderPassStep {
        val packet = packet(
            id = "packet.solid",
            commandId = commandId,
            renderStepIdentity = renderStepIdentity,
            semanticPayload = payload,
            uniformSlot = packetUniformSlot,
        )
        return GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID("task.render")),
            batches = listOf(batch("batch.solid", packet, "task.render")),
        )
    }

    private fun preflightSolid(
        payload: GPUDrawSemanticPayload,
        packetCommandId: Int = 31,
        packetUniformSlot: GPUUniformPayloadSlot? = payload.payloadRef.uniformSlot,
    ): Pair<GPUFramePreflightResult, List<String>> {
        val events = mutableListOf<String>()
        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    solidRenderStep(payload, packetCommandId, packetUniformSlot),
                ),
            ),
        )
        return result to events.toList()
    }

    private fun assertSolidRefusalBeforeSideEffects(
        expectedCode: String,
        payload: GPUDrawSemanticPayload.SolidRect,
        packetUniformSlot: GPUUniformPayloadSlot? = payload.payloadRef.uniformSlot,
        packetRenderStepIdentity: String = "rect.fill.coverage",
    ) {
        val recordingEvents = mutableListOf<String>()
        val recordingResult = preflighter(
            resources = RecordingResourceProvider(recordingEvents),
            completion = RecordingCompletionProvider(recordingEvents),
            surface = RecordingSurfaceProvider(recordingEvents),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    solidRenderStep(
                        payload = payload,
                        packetUniformSlot = packetUniformSlot,
                        renderStepIdentity = packetRenderStepIdentity,
                    ),
                ),
            ),
        )
        assertEquals(
            expectedCode,
            assertIs<GPUFramePreflightResult.Refused>(recordingResult).diagnostic.code.value,
        )
        assertTrue(recordingEvents.isEmpty(), "pure validation side effects: $recordingEvents")

        val nativeEvents = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val concreteResources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val nativeResult = preflighter(
            resources = concreteResources,
            completion = RecordingCompletionProvider(nativeEvents),
            surface = RecordingSurfaceProvider(nativeEvents),
            nativeBoundary = adapter.bindNativeFrameBoundary(
                concreteResources,
                RenderOnlyNativePayloadMaterializer(nativeEvents),
            ),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    solidRenderStep(
                        payload = payload,
                        packetUniformSlot = packetUniformSlot,
                        renderStepIdentity = packetRenderStepIdentity,
                    ),
                ),
            ),
        )
        assertEquals(
            expectedCode,
            assertIs<GPUFramePreflightResult.Refused>(nativeResult).diagnostic.code.value,
        )
        assertTrue(nativeEvents.isEmpty(), "native or ticket side effects: $nativeEvents")
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(0, concreteResources.pendingPhysicalReservationCount)
        assertTrue(concreteResources.telemetry.dumpEvents.isEmpty())
    }

    private fun solidSemantic(
        commandId: Int = 31,
        renderStepIdentity: String = "rect.fill.coverage",
    ): GPUDrawSemanticPayload.SolidRect = GPUSolidPayloadGatherer().gatherSemantic(
        GPUPayloadGatherPlan(
            planHash = "solid.gather",
            commandFamily = "FillRect",
            materialAssemblyHash = "solid.material",
            renderStepIdentity = renderStepIdentity,
            writePlanHash = "solid.write",
            bindingPlanHash = "solid.binding",
            uploadPlanHash = "solid.upload",
            dedupScope = "pass.solid",
        ),
        GPUMaterialPayload(
            materialKeyHash = "solid.material.key",
            payloadClass = "solid-rgba-rect",
            valueFacts = mapOf(
                "command.id" to commandId.toString(),
                "rect.left" to "1.0",
                "rect.top" to "2.0",
                "rect.right" to "3.0",
                "rect.bottom" to "4.0",
                "radii.topLeft" to "0.0",
                "radii.topRight" to "0.0",
                "radii.bottomRight" to "0.0",
                "radii.bottomLeft" to "0.0",
                "color.r" to "0.10",
                "color.g" to "0.20",
                "color.b" to "0.30",
                "color.a" to "1.0",
            ),
            resourceFacts = emptyMap(),
            diagnosticLabel = "solid.$commandId",
        ),
    )

    private fun packet(
        id: String,
        commandId: Int,
        passId: String = "pass.main",
        pipelineKey: String = "pipeline.fill",
        renderStepIdentity: String = "step.fill",
        semanticPayload: GPUDrawSemanticPayload? = null,
        uniformSlot: GPUUniformPayloadSlot? = null,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID(id),
        commandIdValue = commandId,
        analysisRecordId = "analysis.$id",
        passId = passId,
        layerId = "root",
        bindingListId = "bindings.$id",
        insertionReasonCode = "ordered",
        sortKey = commandId.toLong(),
        sortKeyPreimage = "sort.$id",
        renderStepId = GPURenderStepID(renderStepIdentity),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        renderPipelineKey = GPURenderPipelineKey(pipelineKey),
        bindingLayoutHash = "layout.fill",
        uniformSlot = uniformSlot,
        semanticPayload = semanticPayload,
        vertexSourceLabel = "vertices.$id",
        targetStateHash = "target.state",
        originalPaintOrder = commandId,
        resourceGeneration = 1,
    )

    private fun msaaContinuationKey(
        samplePlan: GPUSamplePlan.MultisampleFrame = GPUSamplePlan.MultisampleFrame(4),
    ): GPUSampleContinuationKey = GPUSampleContinuationKey(
        target = GPUTargetIdentity("target.scene"),
        targetGeneration = 1,
        deviceGeneration = GPUDeviceGenerationID(7),
        colorFormat = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("srgb-premul"),
        samplePlan = samplePlan,
        colorAttachment = GPUTargetIdentity("target.scene.msaa"),
        depthStencilAttachment = null,
    )

    private fun batch(id: String, packet: GPUDrawPacket, taskId: String): GPUFrameRenderBatch =
        GPUFrameRenderBatch(
            batchId = id,
            kind = GPUPassBatchKind.SolidFill,
            packets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID(taskId)),
        )

    private fun acquire(): GPUFrameStep.AcquireSurfaceOutput = GPUFrameStep.AcquireSurfaceOutput(
        descriptor = surfaceDescriptor(),
        sourceTaskIds = listOf(GPUTaskID("task.output")),
    )

    private fun surfaceBlit(): GPUFrameStep.SurfaceBlitRenderPassStep = GPUFrameStep.SurfaceBlitRenderPassStep(
        scene = GPUFrameTargetRef("target.scene"),
        output = GPUSurfaceOutputRef("surface.main"),
        sourceTaskIds = listOf(GPUTaskID("task.output")),
    )

    private fun present(): GPUFrameStep.PostSubmitPresentAction = GPUFrameStep.PostSubmitPresentAction(
        output = GPUSurfaceOutputRef("surface.main"),
        sourceTaskIds = listOf(GPUTaskID("task.output")),
    )

    private fun surfaceDescriptor(): GPUSurfaceOutputDescriptor = GPUSurfaceOutputDescriptor(
        output = GPUSurfaceOutputRef("surface.main"),
        width = 4,
        height = 4,
        format = GPUColorFormat("rgba8unorm"),
        targetGeneration = 1,
    )

    private fun framePlan(
        steps: List<GPUFrameStep>,
        capabilities: GPUCapabilities = capabilities(),
    ): GPUFramePlan {
        val frameId = GPUFrameID(7)
        val seal = GPUFrameCapabilitySeal.capture(frameId, GPUDeviceGenerationID(7), capabilities)
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(GPURecordingSeal(GPURecordingID("recording.main"), 0, "compat", "replay", seal.sealHash)),
            steps = steps,
            memoryBudget = budget(),
            diagnostics = emptyList(),
        )
    }

    private fun preflightContext(
        deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(7),
        targetGeneration: Long = 1,
        resourceGeneration: Long = 1,
    ): GPUFramePreflightContext = GPUFramePreflightContext(
        targetId = "target.scene",
        deviceGeneration = deviceGeneration,
        targetGeneration = targetGeneration,
        resourceGenerations = listOf<GPUFrameResourceRef>(
            GPUFrameTargetRef("target.scene"),
            GPUFrameBufferRef("buffer.upload"),
            GPUFrameTextureRef("texture.copy"),
            GPUFrameBufferRef("buffer.readback"),
        ).associateWith { resourceGeneration },
    )

    private fun capabilities(
        snapshotId: String = "capabilities.current",
        maxBufferSize: Long? = 1L shl 30,
    ): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "preflight")),
        snapshotId = snapshotId,
        limits = GPULimits(8192, 256, 256, maxBufferSize = maxBufferSize),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.Readback,
        ),
    )

    private fun budget(): GPUFrameMemoryBudgetPlan = GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 64,
        targetResidentBytes = 64,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = 1L shl 30,
        diagnostic = null,
    )
}
