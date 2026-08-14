package org.graphiks.kanvas.gpu.renderer.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedRenderRunMaterialization
import org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedVerticesRenderRunMaterializer
import org.graphiks.kanvas.gpu.renderer.execution.PreparedVerticesPreflightFixture
import org.graphiks.kanvas.gpu.renderer.execution.preparedVerticesRenderRunTestPlan
import org.graphiks.kanvas.gpu.renderer.execution.verticesPreflightFixture
import org.graphiks.kanvas.gpu.renderer.execution.verticesPreflightSemantic
import org.graphiks.kanvas.gpu.renderer.vertices.PREPARED_VERTICES_BATCH_NONCLAIM_LINE
import org.graphiks.kanvas.gpu.renderer.vertices.RecordingPreparedVerticesBatchNative

/**
 * Deterministic counters for the prepared-vertices batching pass.
 *
 * Counter names are closed enum data, values are foldable longs, and snapshots carry no
 * object identity: equal passes produce byte-equal dump lines.
 */
class PreparedVerticesTelemetryTest {

    @Test
    fun `counter snapshot is deterministic and emits stable dump lines`() {
        val snapshot = GPUPreparedVerticesBatchingCounters.of(
            mapOf(
                GPUPreparedVerticesBatchingCounter.DrawCount to 2L,
                GPUPreparedVerticesBatchingCounter.VertexBytes to 48L,
            ),
        )

        assertEquals(2L, snapshot.counter(GPUPreparedVerticesBatchingCounter.DrawCount))
        assertEquals(48L, snapshot.counter(GPUPreparedVerticesBatchingCounter.VertexBytes))
        assertEquals(0L, snapshot.counter(GPUPreparedVerticesBatchingCounter.Readbacks))
        val lines = snapshot.dumpLines()
        assertEquals(
            "counter:prepared-vertices.draw.count:prepared-vertices.batching:2:count",
            lines.first(),
        )
        assertEquals(
            "counter:prepared-vertices.vertex-bytes:prepared-vertices.batching:48:bytes",
            lines[2],
        )
        assertEquals(PREPARED_VERTICES_BATCH_NONCLAIM_LINE, lines.last())
        assertEquals(snapshot.dumpLines(), snapshot.dumpLines(), "dump lines must be deterministic")
        assertEquals(
            GPUPreparedVerticesBatchingCounters.of(
                mapOf(
                    GPUPreparedVerticesBatchingCounter.DrawCount to 2L,
                    GPUPreparedVerticesBatchingCounter.VertexBytes to 48L,
                ),
            ).dumpLines(),
            snapshot.dumpLines(),
        )
    }

    @Test
    fun `compatible two draw frame emits exact counters`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val rebuilt = fixture.withVerticesSecondSemantic(
            verticesPreflightSemantic(
                commandId = 1,
                artifact = fixture.framePlan.steps
                    .filterIsInstance<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep>()
                    .flatMap { step -> step.drawPackets }
                    .single { packet ->
                        packet.semanticPayload is org.graphiks.kanvas.gpu.renderer.payloads
                            .GPUDrawSemanticPayload.Vertices
                    }
                    .let { packet ->
                        (packet.semanticPayload as org.graphiks.kanvas.gpu.renderer.payloads
                            .GPUDrawSemanticPayload.Vertices).artifact
                    },
            ),
        )
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)
        var observed: GPUPreparedVerticesBatchingCounters? = null
        val materialUniformBytes = plan.packets.first().material.uniformBytes.size

        assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(
                native.device,
                countersObserver = { counters -> observed = counters },
            ).materializeAcceptedRun(
                plan,
                rebuilt.context.deviceGeneration,
                targetViewOperand(rebuilt.context.deviceGeneration, native),
            ),
        )

        val counters = assertIs<GPUPreparedVerticesBatchingCounters>(observed)
        assertEquals(2L, counters.counter(GPUPreparedVerticesBatchingCounter.DrawCount))
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.UniqueArtifacts))
        assertEquals(48L, counters.counter(GPUPreparedVerticesBatchingCounter.VertexBytes))
        assertEquals(12L, counters.counter(GPUPreparedVerticesBatchingCounter.IndexBytes))
        assertEquals(0L, counters.counter(GPUPreparedVerticesBatchingCounter.FanExpansion))
        assertEquals(6L, counters.counter(GPUPreparedVerticesBatchingCounter.BufferCreations))
        assertEquals(6L, counters.counter(GPUPreparedVerticesBatchingCounter.UploadCount))
        assertEquals(
            (48L + 12L + 2L * 64L + 2L * materialUniformBytes).toLong(),
            counters.counter(GPUPreparedVerticesBatchingCounter.UploadBytes),
        )
        assertEquals(2L, counters.counter(GPUPreparedVerticesBatchingCounter.PackedSubranges))
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.PipelineCreations))
        // One SetPipeline per packet: the second packet re-emits the batch's
        // single cached pipeline, which the per-packet facade contract requires.
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.PipelineReuses))
        assertEquals(3L, counters.counter(GPUPreparedVerticesBatchingCounter.LayoutCreations))
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.LayoutReuses))
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.CompatibleBatches))
        assertEquals(0L, counters.counter(GPUPreparedVerticesBatchingCounter.DrawCalls))
        assertEquals(2L, counters.counter(GPUPreparedVerticesBatchingCounter.DrawIndexedCalls))
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.EncoderScopes))
        assertEquals(0L, counters.counter(GPUPreparedVerticesBatchingCounter.QueueSubmits))
        assertEquals(0L, counters.counter(GPUPreparedVerticesBatchingCounter.Readbacks))
    }

    @Test
    fun `incompatible frame increments pipeline creations and leaves batches at zero`() {
        val otherMaterial = compiledPreparedVerticesMaterial(
            org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor(
                r = 0.25f,
                g = 1f,
                b = 1f,
                a = 1f,
            ),
        )
        val fixture = verticesPreflightFixture(commandCount = 1)
        val second = org.graphiks.kanvas.gpu.renderer.execution.verticesPreflightSemantic(
            commandId = 1,
            artifact = fixture.framePlan.steps
                .filterIsInstance<org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep>()
                .flatMap { step -> step.drawPackets }
                .single { packet ->
                    packet.semanticPayload is org.graphiks.kanvas.gpu.renderer.payloads
                        .GPUDrawSemanticPayload.Vertices
                }
                .let { packet ->
                    (packet.semanticPayload as org.graphiks.kanvas.gpu.renderer.payloads
                        .GPUDrawSemanticPayload.Vertices).artifact
                },
            material = otherMaterial,
        )
        val rebuilt = fixture.withVerticesSecondSemantic(second)
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)
        var observed: GPUPreparedVerticesBatchingCounters? = null

        assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(
                native.device,
                countersObserver = { counters -> observed = counters },
            ).materializeAcceptedRun(
                plan,
                rebuilt.context.deviceGeneration,
                targetViewOperand(rebuilt.context.deviceGeneration, native),
            ),
        )

        val counters = assertIs<GPUPreparedVerticesBatchingCounters>(observed)
        assertEquals(2L, counters.counter(GPUPreparedVerticesBatchingCounter.PipelineCreations))
        assertEquals(0L, counters.counter(GPUPreparedVerticesBatchingCounter.PipelineReuses))
        assertEquals(6L, counters.counter(GPUPreparedVerticesBatchingCounter.LayoutCreations))
        assertEquals(0L, counters.counter(GPUPreparedVerticesBatchingCounter.CompatibleBatches))
        assertEquals(4L, counters.counter(GPUPreparedVerticesBatchingCounter.PackedSubranges))
        assertEquals(2L, counters.counter(GPUPreparedVerticesBatchingCounter.DrawIndexedCalls))
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.EncoderScopes))
        assertEquals(0L, counters.counter(GPUPreparedVerticesBatchingCounter.QueueSubmits))
        assertEquals(0L, counters.counter(GPUPreparedVerticesBatchingCounter.Readbacks))
    }

    @Test
    fun `fan canonicalized draw increments the fan expansion counter`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val fanArtifact = org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact(
            topology = org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode.Triangles,
            layout = org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
                .layout(hasColors = false, hasTexCoords = false),
            vertexBytes = ByteArray(6 * 8),
            indexBytes = ByteArray(6 * 2),
            vertexCount = 6,
            indexCount = 6,
            indexFormat = "uint16",
            provenance = "test",
            canonicalizationIdentity =
                org.graphiks.kanvas.gpu.renderer.artifacts
                    .GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1,
        )
        val fanSemantic = org.graphiks.kanvas.gpu.renderer.execution.verticesPreflightSemantic(
            commandId = 0,
            artifact = fanArtifact,
        )
        val rebuilt = fixture.withVerticesFirstSemantic(fanSemantic)
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(rebuilt)
        var observed: GPUPreparedVerticesBatchingCounters? = null

        assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(
                native.device,
                countersObserver = { counters -> observed = counters },
            ).materializeAcceptedRun(
                plan,
                rebuilt.context.deviceGeneration,
                targetViewOperand(rebuilt.context.deviceGeneration, native),
            ),
        )

        val counters = assertIs<GPUPreparedVerticesBatchingCounters>(observed)
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.FanExpansion))
        assertEquals(1L, counters.counter(GPUPreparedVerticesBatchingCounter.DrawCount))
    }

    @Test
    fun `throwing counters observer never turns materialization into refusal nor leaks handles`() {
        val fixture = verticesPreflightFixture(commandCount = 1)
        val native = RecordingPreparedVerticesBatchNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(
                native.device,
                countersObserver = { _ -> error("injected observer failure") },
            ).materializeAcceptedRun(
                plan,
                fixture.context.deviceGeneration,
                targetViewOperand(fixture.context.deviceGeneration, native),
            ),
        )

        val created = native.createdHandles()
        assertTrue(created.isNotEmpty(), "the accepted run must acquire native handles")
        ready.ownedResources.single().close()
        created.forEach { handle ->
            assertEquals(1, native.closeCounts[handle], "close-once for $handle")
        }
    }

    @Test
    fun `counters carry no object identity`() {
        val first = GPUPreparedVerticesBatchingCounters.of(
            mapOf(
                GPUPreparedVerticesBatchingCounter.DrawCount to 3L,
                GPUPreparedVerticesBatchingCounter.VertexBytes to 144L,
            ),
        )
        val second = GPUPreparedVerticesBatchingCounters.of(
            mapOf(
                GPUPreparedVerticesBatchingCounter.VertexBytes to 144L,
                GPUPreparedVerticesBatchingCounter.DrawCount to 3L,
            ),
        )

        assertEquals(first.dumpLines(), second.dumpLines())
        assertEquals(
            first.counter(GPUPreparedVerticesBatchingCounter.DrawCount),
            second.counter(GPUPreparedVerticesBatchingCounter.DrawCount),
        )
        val lines = first.dumpLines()
        assertEquals(
            GPUPreparedVerticesBatchingCounter.entries.size + 1,
            lines.size,
            "one dump line per closed counter plus the non-claim line",
        )
    }

    private fun targetViewOperand(
        generation: org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID,
        native: RecordingPreparedVerticesBatchNative,
    ): org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeTextureViewOperand =
        org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeTextureViewOperand(
            native.targetView,
            generation,
            org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeOperandOwnership.Borrowed,
        )

    private fun compiledPreparedVerticesMaterial(
        descriptor: org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor,
    ): org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram =
        assertIs<org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult.Ready>(
            org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler.compile(
                descriptor = descriptor,
                paintAlpha = 1f,
                context = org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext(
                    capabilityClass = "webgpu-test",
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = "material-dictionary:prepared-material:v1",
                    runtimeEffectResolver =
                        org.graphiks.kanvas.gpu.renderer.runtimeeffects
                            .KanvasPreparedRuntimeEffectResolver(),
                ),
            ),
        ).program
}

private fun PreparedVerticesPreflightFixture.withVerticesFirstSemantic(
    semantic: org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.Vertices,
): PreparedVerticesPreflightFixture {
    val renderIndex = framePlan.steps.indexOfFirst {
        it is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep
    }
    val render = framePlan.steps[renderIndex] as
        org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep
    val packets = render.drawPackets.toMutableList()
    packets[0] = packets[0].rebuilt(semanticPayload = semantic)
    return withRenderPackets(packets, render)
}

private fun PreparedVerticesPreflightFixture.withVerticesSecondSemantic(
    semantic: org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.Vertices,
): PreparedVerticesPreflightFixture {
    val renderIndex = framePlan.steps.indexOfFirst {
        it is org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep
    }
    val render = framePlan.steps[renderIndex] as
        org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep
    val packets = render.drawPackets.toMutableList()
    val template = packets.last()
    packets += template.rebuilt(
        packetId = org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID(
            "packet.vertices.telemetry.second",
        ),
        commandIdValue = 1,
        semanticPayload = semantic,
    )
    return withRenderPackets(packets, render)
}

private fun PreparedVerticesPreflightFixture.withRenderPackets(
    packets: List<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket>,
    render: org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep,
): PreparedVerticesPreflightFixture {
    val rebuiltRender = org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep.RenderPassStep(
        target = render.target,
        loadStore = render.loadStore,
        samplePlan = render.samplePlan,
        resourceUses = render.resourceUses,
        drawPackets = packets,
        sourceTaskIds = render.sourceTaskIds,
        batches = listOf(
            org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch(
                batchId = "batch.vertices.telemetry",
                kind = org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind.Isolated,
                packets = packets,
                sourceTaskIds = render.sourceTaskIds,
            ),
        ),
        sampleContinuation = render.sampleContinuation,
        depthStencilLoadStore = render.depthStencilLoadStore,
        preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
        preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
    )
    return copy(
        framePlan = org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan(
            frameId = framePlan.frameId,
            capabilitySeal = framePlan.capabilitySeal,
            recordingSeals = framePlan.recordingSeals,
            steps = framePlan.steps.map { step ->
                if (step === render) rebuiltRender else step
            },
            memoryBudget = framePlan.memoryBudget,
            diagnostics = framePlan.diagnostics,
            dependencies = framePlan.dependencies,
            phaseOrder = framePlan.phaseOrder,
            elidedNoOpDraws = framePlan.elidedNoOpDraws,
            atomicallyRefused = framePlan.atomicallyRefused,
        ),
    )
}

private fun org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket.rebuilt(
    packetId: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID = this.packetId,
    commandIdValue: Int = this.commandIdValue,
    semanticPayload: org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload? =
        this.semanticPayload,
): org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket = org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket(
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
    bindingLayoutHash = bindingLayoutHash,
    semanticPayload = semanticPayload,
    vertexSourceLabel = vertexSourceLabel,
    targetStateHash = targetStateHash,
    originalPaintOrder = originalPaintOrder,
    resourceGeneration = resourceGeneration,
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoveragePlan,
    clipExecutionPlan = clipExecutionPlan,
)
