package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.io.File
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveDirectClipAuthority
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLease
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest {
    @Test
    fun `direct core materializer integrity gate performs no canonical hash work`() {
        val source = File(
            "src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/" +
                "GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt",
        ).readText()

        assertTrue(source.contains("semantic.hasStructuralIntegrity()"))
        assertFalse(source.contains("hasCanonicalHashIntegrity()"))
        assertFalse(source.contains("MessageDigest"))
        assertFalse(source.contains("SHA-256"))
    }

    @Test
    fun `surface split retains the exact core route seal and accepted geometry instances`() {
        val fixture = fixture()
        val coreScope = fixture.encoderPlan.scopes.single()
        val surfaceScope = GPUCommandEncoderScopePlan(
            sourceStepIndex = fixture.plan.steps.size + 1,
            operationKind = GPUEncoderOperationKind.SurfaceBlit,
            sourceTaskIds = listOf(GPUTaskID("task.surface.blit")),
            facadeOperationClasses = listOf("beginRenderPass", "surfaceBlit", "endRenderPass"),
            targetGeneration = fixture.encoderPlan.targetGeneration,
            resourceGenerationLabels = listOf("GPUFrameTargetRef:target.core.proxy@1"),
        )
        val fullPlan = GPUCommandEncoderPlan.ordered(
            planId = fixture.encoderPlan.planId,
            contextIdentity = fixture.encoderPlan.contextIdentity,
            deviceGeneration = fixture.encoderPlan.deviceGeneration,
            targetGeneration = fixture.encoderPlan.targetGeneration,
            scopes = listOf(coreScope, surfaceScope),
        )

        val reusable = wgpu4kReusableEncoderPlanWithoutSurface(fullPlan)

        val retainedScope = reusable.scopes.single()
        assertSame(coreScope, retainedScope)
        assertSame(
            coreScope.corePrimitiveDirectNativeRouteSeal,
            retainedScope.corePrimitiveDirectNativeRouteSeal,
        )
        val originalRoutes = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            coreScope.corePrimitiveDirectNativeRouteSeal,
        )
        val retainedRoutes = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            retainedScope.corePrimitiveDirectNativeRouteSeal,
        )
        originalRoutes.routesByPacketId.forEach { (packetId, accepted) ->
            assertSame(accepted, retainedRoutes.routesByPacketId.getValue(packetId))
        }
        fixture.close()
    }

    @Test
    fun `refusal performs no native action`() {
        val fixture = fixture()
        val events = fixture.native.events
        events.clear()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val refused = materializer.materializeReusable(
            fixture.plan,
            fixture.encoderPlan,
            GPUPreparedResourceSet(emptyList(), emptyList()),
            fixture.generationSeal,
        )

        assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(refused)
        assertEquals(emptyList(), events)
        materializer.close()
        fixture.close()
    }

    @Test
    fun `two draws share one render pass one dynamic uniform slab and one bind group`() {
        val fixture = fixture()
        val events = fixture.native.events
        events.clear()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val result = materializer.materializeReusable(
                fixture.plan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            )
        val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            result,
            (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)?.let { "${it.code}: ${it.message}" },
        )
        val renders = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        assertEquals(1, renders.size)
        val render = renders.single()
        val vertices = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
        val indices = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>()
        val setBindGroups = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
        val bindGroups = setBindGroups.map(GPUPreparedNativeRenderCommand.SetBindGroup::bindGroup)
        val draws = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>()
        assertEquals(1, render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size)
        assertEquals(1, vertices.size)
        assertEquals(1, indices.size)
        assertEquals(0L, vertices.single().offset)
        assertEquals(64L, vertices.single().size)
        assertEquals(0L, indices.single().offset)
        assertEquals(48L, indices.single().size)
        assertEquals(8L, vertices.single().vertexStrideBytes)
        assertEquals(16L * 1024L, vertices.single().buffer.byteCapacity)
        assertEquals(4L * 1024L, indices.single().buffer.byteCapacity)
        assertEquals(listOf(0, 6), draws.map { it.drawCall.firstIndex })
        assertEquals(listOf(0, 4), draws.map { it.drawCall.baseVertex })
        assertEquals(listOf(4, 4), draws.map { it.drawCall.vertexCount })
        assertEquals(listOf(3, 3), draws.map { it.drawCall.maxLocalIndex })
        assertEquals(
            listOf("pipeline", "vertex", "index", "bind", "scissor", "draw", "bind", "scissor", "draw"),
            render.commands.map { command ->
                when (command) {
                    is GPUPreparedNativeRenderCommand.SetPipeline -> "pipeline"
                    is GPUPreparedNativeRenderCommand.SetVertexBuffer -> "vertex"
                    is GPUPreparedNativeRenderCommand.SetIndexBuffer -> "index"
                    is GPUPreparedNativeRenderCommand.SetBindGroup -> "bind"
                    is GPUPreparedNativeRenderCommand.SetScissor -> "scissor"
                    is GPUPreparedNativeRenderCommand.DrawIndexed -> "draw"
                    is GPUPreparedNativeRenderCommand.SetStencilReference -> "stencil"
                    is GPUPreparedNativeRenderCommand.Draw -> "non-indexed-draw"
                }
            },
        )
        assertEquals(listOf(listOf(0L), listOf(256L)), setBindGroups.map { it.dynamicOffsets })
        assertEquals(1, bindGroups.distinctBy { System.identityHashCode(it.bindGroup) }.size)
        assertTrue(bindGroups.all { it.ownership == GPUPreparedNativeOperandOwnership.Borrowed })
        assertTrue(vertices.all { it.buffer.ownership == GPUPreparedNativeOperandOwnership.Borrowed })
        assertTrue(indices.all { it.buffer.ownership == GPUPreparedNativeOperandOwnership.Borrowed })
        assertEquals(1, events.count { it == "createBuffer:Kanvas.session.corePrimitive.framePool.vertices" })
        assertEquals(1, events.count { it == "createBuffer:Kanvas.session.corePrimitive.framePool.indices" })
        assertEquals(1, events.count { it == "createBuffer:Kanvas.session.corePrimitive.framePool.uniforms" })
        assertEquals(1, events.count { it == "createBindGroup:Kanvas.session.corePrimitive.framePool.bindGroup0" })
        assertEquals(3, events.count { it == "writeBuffer" })
        val uniformBinding = requireNotNull(
            fixture.native.bindGroupLayoutDescriptors.single().entries.single().buffer,
        )
        assertTrue(uniformBinding.hasDynamicOffset)
        assertEquals(32uL, uniformBinding.minBindingSize)

        assertTrue(materialized.draft.disposeBeforeRegistration())
        assertEquals(0, fixture.native.closeCounts.getOrDefault(vertices[0].buffer.buffer, 0))
        assertEquals(0, fixture.native.closeCounts.getOrDefault(indices[0].buffer.buffer, 0))
        materializer.close()
        fixture.close()
        assertEquals(1, fixture.native.closeCounts.getOrDefault(vertices[0].buffer.buffer, 0))
        assertEquals(1, fixture.native.closeCounts.getOrDefault(indices[0].buffer.buffer, 0))
    }

    @Test
    fun `core uploads pass explicit zero data offsets and exact used byte ranges`() {
        val fixture = fixture()
        fixture.native.events.clear()
        fixture.native.writeBufferCalls.clear()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )
        val uniformUsedBytes = fixture.plan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .single { it.role == GPUFrameResourceRole.UniformData }
            .byteSize
            .toULong()

        val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            materializer.materializeReusable(
                fixture.plan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            ),
        )

        assertEquals(
            listOf(
                WriteBufferCall("Kanvas.session.corePrimitive.framePool.vertices", 0uL, 0uL, 64uL, 64uL),
                WriteBufferCall("Kanvas.session.corePrimitive.framePool.indices", 0uL, 0uL, 48uL, 48uL),
                WriteBufferCall(
                    "Kanvas.session.corePrimitive.framePool.uniforms",
                    0uL,
                    0uL,
                    uniformUsedBytes,
                    uniformUsedBytes,
                ),
            ),
            fixture.native.writeBufferCalls,
        )
        assertTrue(materialized.draft.disposeBeforeRegistration())
        materializer.close()
        fixture.close()
    }

    @Test
    fun `sequential submitted frames reuse the exact pooled handles after successful completion`() {
        val fixture = fixture()
        fixture.native.events.clear()
        fixture.native.writeBufferCalls.clear()
        val first = fixture.materializeCore()
        val firstRender = first.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .single()
        val firstVertex = firstRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
            .first().buffer.buffer
        val firstIndex = firstRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>()
            .first().buffer.buffer
        val firstBindGroup = firstRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
            .first().bindGroup.bindGroup
        val adapter = GPURuntimeResourceAdapter()
        val registration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(first.draft),
        )
        assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
            registration.ownership.bindLateSurface(null, GPUPreparedNativeFrameLateSurfaceBinding.NotRequired),
        )
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            registration.ownership.consume(first.draft.payload.identity),
        )
        assertTrue(registration.ownership.markSubmitted())
        assertTrue(registration.ownership.releaseAfterCompletion())

        val second = fixture.materializeCore()
        val secondRender = second.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .single()
        assertSame(
            firstVertex,
            secondRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>().first().buffer.buffer,
        )
        assertSame(
            firstIndex,
            secondRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>().first().buffer.buffer,
        )
        assertSame(
            firstBindGroup,
            secondRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>().first().bindGroup.bindGroup,
        )
        assertEquals(1, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.vertices").size)
        assertEquals(1, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.indices").size)
        assertEquals(1, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.uniforms").size)
        assertEquals(1, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.bindGroup0").size)
        assertEquals(6, fixture.native.writeBufferCalls.size)

        assertTrue(second.draft.disposeBeforeRegistration())
        adapter.close()
        fixture.close()
    }

    @Test
    fun `three in flight frames use distinct slots fourth refuses and rollback reuses immediately`() {
        val fixture = fixture()
        fixture.native.events.clear()
        val live = List(3) { fixture.materializeCore().draft }
        val vertices = live.map { draft ->
            draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>().single()
                .commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>().first().buffer.buffer
        }
        assertNotSame(vertices[0], vertices[1])
        assertNotSame(vertices[1], vertices[2])

        val fourth = fixture.materializeCoreResult()

        assertEquals(
            "unsupported.native-core-primitive.frame-pool-saturated",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(fourth).code,
        )
        assertEquals(3, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.vertices").size)
        assertTrue(live.first().disposeBeforeRegistration())
        val reused = fixture.materializeCore().draft
        val reusedVertex = reused.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>().single()
            .commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>().first().buffer.buffer
        assertSame(vertices.first(), reusedVertex)
        assertEquals(3, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.vertices").size)

        live.drop(1).forEach { assertTrue(it.disposeBeforeRegistration()) }
        assertTrue(reused.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `uniform byte corruption refuses before every native action`() {
        val fixture = fixture()
        val render = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().first()
        val packet = render.drawPackets.first()
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
        val block = requireNotNull(semantic.payloadRef.uniformBlock)
        val corruptedBytes = block.bytes.toMutableList().also { it[it.lastIndex] = it.last() xor 0xff }
        val corrupted = GPUDrawSemanticPayload.CorePrimitive(
            semantic.payloadRef.copy(uniformBlock = block.copy(bytes = corruptedBytes)),
            semantic.sourceFamily,
            semantic.geometry,
            semantic.premultipliedRgba,
            semantic.targetBounds,
            semantic.scissorBounds,
            semantic.clipCoveragePlan,
            semantic.clipExecutionPlanIdentity,
            semantic.blendPlanIdentity,
            semantic.frameProvenance,
            semantic.canonicalHash,
            semantic.coverageMode,
        )
        val plan = fixture.plan.replacingPacket(packet, packet.withSemantic(corrupted))
        fixture.native.events.clear()
        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        ).materializeReusable(plan, fixture.encoderPlan, fixture.resources, fixture.generationSeal)

        assertEquals(
            "invalid.native-core-primitive.packet-authority",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `shifted full size readback refuses before every native action`() {
        val fixture = fixture(readback = true)
        val readback = fixture.plan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().single()
        val shifted = GPUFrameStep.ReadbackCopyStep(
            readback.source,
            readback.staging,
            GPUFrameReadbackRequest(
                readback.request.requestId,
                GPUPixelBounds(1, 0, TARGET.width + 1, TARGET.height),
                readback.request.pixelFormat,
                readback.request.outputColorInterpretation,
            ),
            readback.sourceTaskIds,
        )
        val plan = fixture.plan.replacingStep(readback, shifted)
        fixture.native.events.clear()
        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        ).materializeReusable(plan, fixture.encoderPlan, GPUPreparedResourceSet(emptyList(), emptyList()), fixture.generationSeal)

        assertEquals(
            "unsupported.native-core-primitive.readback-layout",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `forged host unaddressable descriptor cannot replace the sealed uniform slab`() {
        val fixture = fixture()
        val alignment = 1L shl 31
        val totalBytes = 1L shl 32
        val plan = fixture.plan.withUniformPreparation(totalBytes, alignment)
        fixture.native.events.clear()

        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits.copy(
                minUniformBufferOffsetAlignment = alignment,
                maxBufferSize = 1L shl 33,
            ),
        ).materializeReusable(plan, fixture.encoderPlan, fixture.resources, fixture.generationSeal)

        assertEquals(
            "invalid.native-core-primitive.uniform-seal-generation",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `allocation upload and bind group failures close every created frame handle once`() {
        val vertex = "Kanvas.session.corePrimitive.framePool.vertices"
        val index = "Kanvas.session.corePrimitive.framePool.indices"
        val uniform = "Kanvas.session.corePrimitive.framePool.uniforms"
        val bindGroup = "Kanvas.session.corePrimitive.framePool.bindGroup0"
        listOf(
            FailureCase(
                "createBuffer",
                1,
                emptySet(),
                expectedCode = "failed.native-core-primitive.frame-pool-allocation",
            ),
            FailureCase(
                "createBuffer",
                2,
                setOf(vertex),
                expectedCode = "failed.native-core-primitive.frame-pool-allocation",
            ),
            FailureCase(
                "createBuffer",
                3,
                setOf(vertex, index),
                expectedCode = "failed.native-core-primitive.frame-pool-allocation",
            ),
            FailureCase(
                "createBuffer",
                4,
                setOf(vertex, index, uniform, bindGroup),
                readback = true,
            ),
            FailureCase("writeBuffer", 1, setOf(vertex, index, uniform, bindGroup)),
            FailureCase(
                "writeBuffer",
                2,
                setOf(vertex, index, uniform, bindGroup),
            ),
            FailureCase(
                "writeBuffer",
                3,
                setOf(vertex, index, uniform, bindGroup),
            ),
            FailureCase(
                "createBindGroup",
                1,
                setOf(vertex, index, uniform),
                expectedCode = "failed.native-core-primitive.frame-pool-allocation",
            ),
        ).forEach { failureCase ->
            val fixture = fixture(readback = failureCase.readback)
            fixture.native.events.clear()
            fixture.native.fail(failureCase.operation, failureCase.ordinal)
            val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                fixture.native.device,
                fixture.native.queue,
                fixture.target,
                fixture.cache,
                fixture.limits,
            )

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                materializer.materializeReusable(
                    fixture.plan,
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                ),
            )

            assertEquals(failureCase.expectedCode, refused.code)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            materializer.close()
            fixture.close()
            assertEquals(
                failureCase.closedLabels.associateWith { 1 },
                fixture.native.coreCloseAttempts(),
            )
        }
    }

    @Test
    fun `failed pooled handle close is retained by the session and retried without double close`() {
        val fixture = fixture()
        fixture.native.events.clear()
        fixture.native.fail("createBindGroup", 1)
        fixture.native.failCloseOnce("Kanvas.session.corePrimitive.framePool.uniforms")
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
            materializer.materializeReusable(
                fixture.plan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            ),
        )
        assertEquals("failed.native-core-primitive.frame-pool-allocation", refused.code)
        assertEquals(null, refused.retainedPreRegistrationLedger)

        materializer.close()
        fixture.close()
        assertEquals(
            mapOf(
                "Kanvas.session.corePrimitive.framePool.vertices" to 1,
                "Kanvas.session.corePrimitive.framePool.indices" to 1,
                "Kanvas.session.corePrimitive.framePool.uniforms" to 2,
            ),
            fixture.native.coreCloseAttempts(),
        )
    }

    @Test
    fun `readback staging is output owned and draft rollback closes it once`() {
        val fixture = fixture(readback = true)
        fixture.native.events.clear()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            materializer.materializeReusable(
                fixture.plan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            ),
        )
        val readback = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
            .single()
        assertEquals(GPUPreparedNativeOperandOwnership.OutputOwnedReadback, readback.destination.ownership)
        assertEquals(1, fixture.native.events.count {
            it == "createBuffer:Kanvas.frame.corePrimitive.readback"
        })
        assertTrue(materialized.draft.disposeBeforeRegistration())
        assertEquals(
            1,
            fixture.native.coreCloseAttempts().getValue("Kanvas.frame.corePrimitive.readback"),
        )
        materializer.close()
        fixture.close()
    }

    private fun fixture(readback: Boolean = false): Fixture {
        val generation = GPUDeviceGenerationID(23L)
        val capabilities = capabilities()
        val frameId = GPUFrameID(231L)
        val base = GPURecorder(GPURecordingID("recording.core.proxy"), frameId, capabilities, generation).apply {
            record(command(1, 0, GPURect(1f, 1f, 5f, 5f)))
            record(command(2, 1, GPURect(6f, 2f, 10f, 6f)))
        }.close().taskList.withNoClip()
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = packets.associate { packet -> packet.commandIdValue to semantic(packet) }
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = base,
                    capabilities = capabilities,
                    target = GPUFrameTargetRef("target.core.proxy"),
                    targetBounds = TARGET,
                    semanticsByCommandId = semantics,
                    readbackRequestId = if (readback) GPUReadbackRequestID("readback.core.proxy") else null,
                ),
            ),
        ).taskList
        val plan = GPUFramePlanner.plan(taskList)
        check(!plan.atomicallyRefused) { plan.dumpLines().joinToString("\n") }
        val generations = plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .mapIndexed { index, request -> request.resource to (index + 1L) }
            .toMap()
        val renderScopes = plan.steps.withIndex().mapNotNull { (index, step) ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@mapNotNull null
            val stream = commandStream(render.drawPackets, generation)
            GPUCommandEncoderScopePlan(
                sourceStepIndex = index,
                operationKind = GPUEncoderOperationKind.Render,
                scopeLabel = "step.$index",
                sourceTaskIds = render.sourceTaskIds,
                sourcePacketIds = render.drawPackets.map(GPUDrawPacket::packetId),
                facadeOperationClasses = stream.commandLabels,
                targetGeneration = 1L,
                resourceGenerationLabels = listOf("target@1", "vertex@2", "index@3", "uniform@4"),
                passCommandStream = stream,
                corePrimitiveDirectNativeRouteSeal = GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(
                    render.drawPackets.associate { packet ->
                        val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                        packet.packetId to assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
                            validateCorePrimitiveDirectNativeRoute(
                                semantic,
                                corePrimitiveDirectClipAuthority(
                                    requireNotNull(packet.clipExecutionPlan),
                                    semantic.targetBounds,
                                ),
                                packet.blendPlan,
                                render.samplePlan,
                                "rgba8unorm",
                            ),
                        )
                    },
                    preparedPassSeal = requireNotNull(
                        render.drawPackets.first().corePrimitivePreparedAuthority,
                    ).let { authority ->
                        GPUCorePrimitiveDirectPreparedPassSeal(
                            authority.structuralPipelineKey,
                            requireNotNull(authority.uniformSlabSeal),
                        )
                    },
                ),
            ).attachNativeOperandKeys(renderOperandKeys(render.drawPackets.first().commandIdValue))
        }
        val readbackScopes = plan.steps.withIndex().mapNotNull { (index, step) ->
            val readbackStep = step as? GPUFrameStep.ReadbackCopyStep ?: return@mapNotNull null
            GPUCommandEncoderScopePlan(
                sourceStepIndex = index,
                operationKind = GPUEncoderOperationKind.Readback,
                sourceTaskIds = readbackStep.sourceTaskIds,
                facadeOperationClasses = listOf("copyTextureToBuffer"),
                targetGeneration = 1L,
                resourceGenerationLabels = listOf(
                    "target@${generations.getValue(readbackStep.source)}",
                    "staging@${generations.getValue(readbackStep.staging)}",
                ),
            ).attachNativeOperandKeys(readbackOperandKeys())
        }
        val encoderPlan = GPUCommandEncoderPlan.ordered(
            planId = "core.proxy.encoder",
            contextIdentity = "core.proxy",
            deviceGeneration = generation,
            targetGeneration = 1L,
            scopes = renderScopes + readbackScopes,
        )
        val resources = GPUPreparedResourceSet(
            ordinaryResources = plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .filter { it.role != GPUFrameResourceRole.ReadbackStaging }
                .map { request ->
                    GPUPreparedResourceEvidence(
                        logicalResource = request.resource,
                        concreteResource = if (request.role == GPUFrameResourceRole.SceneTarget) {
                            GPUPreparedConcreteResourceRef.Texture(GPUTextureResourceRef("prepared.target"))
                        } else {
                            GPUPreparedConcreteResourceRef.Buffer(GPUBufferResourceRef("prepared.${request.resource.value}"))
                        },
                        role = request.role,
                        deviceGeneration = generation,
                        resourceGeneration = generations.getValue(request.resource),
                    )
                },
            outputOwnedReadbacks = if (!readback) {
                emptyList()
            } else {
                val readbackStep = plan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().single()
                val layout = assertIs<GPUReadbackLayoutPlan.Planned>(
                    GPUReadbackLayoutPlanner().plan(readbackStep.request, capabilities),
                ).layout
                val concrete = GPUBufferResourceRef("prepared.${readbackStep.staging.value}")
                listOf(
                    GPUPreparedReadbackOutput(
                        stagingResource = readbackStep.staging,
                        concreteResource = GPUPreparedConcreteResourceRef.Buffer(concrete),
                        resourceGeneration = generations.getValue(readbackStep.staging),
                        request = readbackStep.request,
                        layout = layout,
                        stagingLease = GPUReadbackStagingLease(
                            reservationId = "reservation.core.proxy",
                            ownerScope = "frame.core.proxy",
                            deviceGeneration = generation,
                            resourceRef = concrete,
                            reservationOrdinal = 1L,
                            acquisitionToken = 1L,
                            logicalMinimumBytes = layout.totalBufferBytes,
                            backingBufferBytes = layout.totalBufferBytes,
                            usages = setOf(
                                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.MapRead,
                                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.CopyDestination,
                            ),
                        ),
                    ),
                )
            },
        )
        val native = NativeProxy()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            native.device,
            TARGET.width,
            TARGET.height,
            generation,
            1L,
            GPUWgpu4kPreparedSceneTargetLifecycle(),
            setup,
        )
        setup.commit()
        return Fixture(
            plan,
            encoderPlan,
            resources,
            GPUPreparedGenerationSeal(generation, 1L, generations, plan.capabilitySeal.sealHash),
            native,
            target,
            GPUWgpu4kCorePrimitiveSessionCache(native.device, generation),
            requireNotNull(capabilities.limits),
        )
    }

    private fun commandStream(packets: List<GPUDrawPacket>, generation: GPUDeviceGenerationID): GPUPassCommandStream {
        fun operand(kind: GPUMaterializedCommandOperandKind, label: String) =
            GPUMaterializedCommandOperandReference(
                label,
                kind,
                "descriptor.$label",
                generation.value,
                "core.proxy",
                listOf("render"),
                "frame-local",
            )
        val bridges = packets.flatMap { packet ->
            listOf(
                GPUPassCommandOperandBridge(packet.packetId, "setRenderPipeline", operand(GPUMaterializedCommandOperandKind.RenderPipeline, "pipeline")),
                GPUPassCommandOperandBridge(packet.packetId, "setBindGroup", operand(GPUMaterializedCommandOperandKind.BindGroup, "bind")),
                GPUPassCommandOperandBridge(packet.packetId, "draw", operand(GPUMaterializedCommandOperandKind.VertexBuffer, "vertex")),
                GPUPassCommandOperandBridge(packet.packetId, "draw", operand(GPUMaterializedCommandOperandKind.IndexBuffer, "index")),
            )
        }
        val first = packets.first()
        return GPUPassCommandStream.fromDrawPacketStream(
            "stream.${first.commandIdValue}",
            GPUDrawPacketStream("packets.${first.commandIdValue}", first.passId, packets),
            first.targetStateHash,
            "store",
            operandBridge = bridges,
        )
    }

    private fun renderOperandKeys(commandId: Int): List<GPUPreparedNativeOperandKey> {
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            ownership: GPUPreparedNativeOperandOwnership,
            ordinal: Int = 0,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey("core.$commandId.${role.name}.$ordinal"),
            ownership,
        )
        return listOf(
            key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, GPUPreparedNativeOperandOwnership.Borrowed, 1),
        )
    }

    private fun readbackOperandKeys(): List<GPUPreparedNativeOperandKey> = listOf(
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.ReadbackSource,
            GPUPreparedNativeOperandKind.Texture,
            gpuPreparedNativeBindingKey("core.readback.source"),
            GPUPreparedNativeOperandOwnership.Borrowed,
        ),
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.ReadbackDestination,
            GPUPreparedNativeOperandKind.Buffer,
            gpuPreparedNativeBindingKey("core.readback.destination"),
            GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
        ),
    )

    private fun semantic(packet: GPUDrawPacket): GPUDrawSemanticPayload.CorePrimitive =
        GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = packet.commandIdValue,
                sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 5f, 5f),
                premultipliedRgba = listOf(0.5f, 0f, 0f, 0.5f),
                targetBounds = TARGET,
                scissorBounds = TARGET,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.GmContent,
                analysisRecordId = "analysis.fill_rect.${packet.commandIdValue}",
                analysisCommandFamily = "FillRect",
                rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                rectGeometryAuthority = corePrimitiveRectGeometryAuthority(
                    GPURect(1f, 1f, 5f, 5f),
                    GPUTransformFacts.identity(),
                ),
            ),
        )

    private fun command(id: Int, order: Int, rect: GPURect) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(id),
        rect = rect,
        target = GPUTargetFacts(TARGET.width, TARGET.height, "rgba8unorm"),
        material = GPUMaterialDescriptor.SolidColor(0.5f, 0f, 0f, 0.5f),
        clip = GPUClipFacts(
            kind = GPUClipKind.WideOpen,
            bounds = GPUBounds(0f, 0f, 16f, 16f),
            coveragePlan = GPUClipCoveragePlan.NoClip,
        ),
        paintOrder = order,
        source = GPUCommandSource("unit-test", "core-proxy", GPUFrameProvenance.GmContent),
    )

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "unit", "supported", true, "core"),
            GPUCapabilityFact("first_slice.scissor.native", "unit", "supported", true, "core"),
        ),
        snapshotId = "core-proxy",
        limits = GPULimits(
            8192,
            256,
            256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )

    private fun GPUTaskList.withNoClip(): GPUTaskList = GPUTaskList(
        frameId,
        capabilitySeal,
        recordingSeals,
        expectedReplayKeyHash,
        tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val packets = task.drawPackets.map { packet -> packet.withNoClip() }
            GPUTask.Render(
                task.taskId,
                task.recordingId,
                task.phase,
                task.target,
                task.loadStore,
                task.samplePlan,
                task.resourceUses,
                task.provisionalSegmentKey,
                packets,
                packets.associate { it.packetId to requireNotNull(task.batchEligibilityByPacketId[it.packetId]) },
                task.sampleContinuationKey,
                task.compositeMembership,
            )
        },
        dependencies,
        phaseOrder,
        memoryBudget,
        diagnostics,
    )

    private fun GPUDrawPacket.withNoClip() = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role,
        blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot,
        semanticPayload, vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder,
        resourceGeneration, frameProvenance, clipCoveragePlan, GPUClipExecutionPlan.NoClip, diagnostics,
    )

    private fun GPUDrawPacket.withSemantic(semantic: GPUDrawSemanticPayload.CorePrimitive) = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role,
        blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot,
        semantic, vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder,
        resourceGeneration, frameProvenance, clipCoveragePlan, clipExecutionPlan, diagnostics,
    )

    private fun GPUFramePlan.replacingPacket(
        original: GPUDrawPacket,
        replacement: GPUDrawPacket,
    ): GPUFramePlan = replacingStep(
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { original in it.drawPackets },
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { original in it.drawPackets }
            .withPacket(original, replacement),
    )

    private fun GPUFrameStep.RenderPassStep.withPacket(
        original: GPUDrawPacket,
        replacement: GPUDrawPacket,
    ): GPUFrameStep.RenderPassStep {
        val packets = drawPackets.map { if (it === original) replacement else it }
        return GPUFrameStep.RenderPassStep(
            target,
            loadStore,
            samplePlan,
            resourceUses,
            packets,
            sourceTaskIds,
            batches.map { batch ->
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch(
                    batch.batchId,
                    batch.kind,
                    batch.packets.map { if (it === original) replacement else it },
                    batch.sourceTaskIds,
                )
            },
            sampleContinuation,
            depthStencilLoadStore,
        )
    }

    private fun GPUFramePlan.replacingStep(original: GPUFrameStep, replacement: GPUFrameStep) = GPUFramePlan(
        frameId,
        capabilitySeal,
        recordingSeals,
        steps.map { if (it === original) replacement else it },
        memoryBudget,
        diagnostics,
        dependencies,
        phaseOrder,
        elidedNoOpDraws,
        atomicallyRefused,
    )

    private fun GPUFramePlan.withUniformPreparation(
        byteSize: Long,
        alignmentBytes: Long,
    ): GPUFramePlan {
        val preparation = steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().single()
        val uniform = preparation.requests.single { it.role == GPUFrameResourceRole.UniformData }
        val replacement = GPUResourcePreparationRequest(
            uniform.resource,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor(byteSize, alignmentBytes),
            uniform.role,
            uniform.usages,
            uniform.lifetime,
            byteSize,
            uniform.diagnosticLabel,
        )
        return replacingStep(
            preparation,
            GPUFrameStep.PrepareResourcesStep(
                preparation.requests.map { if (it === uniform) replacement else it },
                preparation.sourceTaskIds,
            ),
        )
    }

    private data class Fixture(
        val plan: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
        val encoderPlan: GPUCommandEncoderPlan,
        val resources: GPUPreparedResourceSet,
        val generationSeal: GPUPreparedGenerationSeal,
        val native: NativeProxy,
        val target: GPUWgpu4kPreparedSceneTarget,
        val cache: GPUWgpu4kCorePrimitiveSessionCache,
        val limits: GPULimits,
    ) {
        fun materializeCoreResult(): GPUPreparedNativeFramePayloadMaterialization {
            val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                native.device,
                native.queue,
                target,
                cache,
                limits,
            )
            return materializer.materializeReusable(plan, encoderPlan, resources, generationSeal).also {
                materializer.close()
            }
        }

        fun materializeCore(): GPUPreparedNativeFramePayloadMaterialization.Materialized {
            val result = materializeCoreResult()
            return assertIs(
                result,
                (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)?.let {
                    "${it.code}: ${it.message}"
                },
            )
        }

        fun close() {
            cache.close()
            target.close()
        }
    }

    private class NativeProxy {
        val events = mutableListOf<String>()
        val writeBufferCalls = mutableListOf<WriteBufferCall>()
        val bindGroupLayoutDescriptors = mutableListOf<BindGroupLayoutDescriptor>()
        val closeCounts = IdentityHashMap<Any, Int>()
        private val createdHandlesByLabel = linkedMapOf<String, MutableList<Any>>()
        private val closeAttemptsByLabel = linkedMapOf<String, Int>()
        private var failingOperation: String? = null
        private var failingOperationOrdinal = 0
        private var operationInvocationCount = 0
        private var failingCloseLabel: String? = null
        private var closeFailureConsumed = false
        private val view = handle(GPUTextureView::class.java, "target.view")
        private val texture = handle(GPUTexture::class.java, "target.texture") { method ->
            if (method.name == "createView") view else null
        }
        val device: GPUDevice = proxy(GPUDevice::class.java) { method, args ->
            when (method.name) {
                "createTexture" -> texture
                "createBuffer" -> {
                    val label = (args?.firstOrNull() as BufferDescriptor).label.orEmpty()
                    events += "createBuffer:$label"
                    failIfRequested("createBuffer")
                    recordedHandle(GPUBuffer::class.java, label)
                }
                "createBindGroup" -> {
                    val label = (args?.firstOrNull() as BindGroupDescriptor).label.orEmpty()
                    events += "createBindGroup:$label"
                    failIfRequested("createBindGroup")
                    recordedHandle(method.returnType, label)
                }
                "createBindGroupLayout" -> {
                    bindGroupLayoutDescriptors += args?.firstOrNull() as BindGroupLayoutDescriptor
                    events += method.name
                    handle(method.returnType, method.name)
                }
                "createShaderModule", "createPipelineLayout", "createRenderPipeline" -> {
                    events += method.name
                    handle(method.returnType, method.name)
                }
                else -> defaultValue(method.returnType)
            }
        }
        val queue: GPUQueue = proxy(GPUQueue::class.java) { method, args ->
            if (method.name.startsWith("writeBuffer")) {
                events += "writeBuffer"
                val data = args?.getOrNull(2) as io.ygdrasil.webgpu.ArrayBuffer
                writeBufferCalls += WriteBufferCall(
                    bufferLabel = args[0].toString(),
                    bufferOffset = (args[1] as Long).toULong(),
                    dataOffset = (args[3] as Long).toULong(),
                    dataBytes = data.size,
                    size = args[4] as ULong?,
                )
                failIfRequested("writeBuffer")
            }
            defaultValue(method.returnType)
        }

        fun fail(operation: String, ordinal: Int) {
            require(operation in setOf("createBuffer", "writeBuffer", "createBindGroup") && ordinal > 0)
            failingOperation = operation
            failingOperationOrdinal = ordinal
            operationInvocationCount = 0
        }

        fun failCloseOnce(label: String) {
            failingCloseLabel = label
            closeFailureConsumed = false
        }

        fun coreCloseAttempts(): Map<String, Int> = closeAttemptsByLabel.filterKeys { label ->
            label.startsWith("Kanvas.frame.corePrimitive.") ||
                label.startsWith("Kanvas.session.corePrimitive.framePool.")
        }

        fun createdHandles(label: String): List<Any> = createdHandlesByLabel[label].orEmpty()

        private fun failIfRequested(operation: String) {
            if (failingOperation != operation) return
            operationInvocationCount += 1
            if (operationInvocationCount == failingOperationOrdinal) {
                error("injected $operation failure")
            }
        }

        private fun <T> handle(
            type: Class<T>,
            label: String,
            extra: (java.lang.reflect.Method) -> Any? = { null },
        ): T = proxy(type) { method, _ ->
            when (method.name) {
                "close" -> {
                    val proxy = currentProxy.get()
                    closeCounts[proxy] = closeCounts.getOrDefault(proxy, 0) + 1
                    closeAttemptsByLabel[label] = closeAttemptsByLabel.getOrDefault(label, 0) + 1
                    events += "close:$label"
                    if (label == failingCloseLabel && !closeFailureConsumed) {
                        closeFailureConsumed = true
                        error("injected close failure")
                    }
                    null
                }
                "toString" -> label
                else -> extra(method) ?: defaultValue(method.returnType)
            }
        }

        private fun recordedHandle(type: Class<*>, label: String): Any =
            handle(type, label).also { created ->
                createdHandlesByLabel.getOrPut(label) { mutableListOf() } += created
            }

        private val currentProxy = ThreadLocal<Any>()

        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(
            type: Class<T>,
            action: (java.lang.reflect.Method, Array<out Any?>?) -> Any?,
        ): T {
            var created: Any? = null
            created = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                currentProxy.set(proxy)
                try {
                    when (method.name) {
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> action(method, args)
                    }
                } finally {
                    currentProxy.remove()
                }
            }
            return created as T
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    private data class FailureCase(
        val operation: String,
        val ordinal: Int,
        val closedLabels: Set<String>,
        val readback: Boolean = false,
        val expectedCode: String = "failed.native-core-primitive.materialization",
    )

    private data class WriteBufferCall(
        val bufferLabel: String,
        val bufferOffset: ULong,
        val dataOffset: ULong,
        val dataBytes: ULong,
        val size: ULong?,
    )

    private companion object {
        val TARGET = GPUPixelBounds(0, 0, 16, 16)
    }
}
