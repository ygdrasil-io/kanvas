package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds as GPUClipBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
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
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

/** Public-wgpu4k smoke for the prepared clip-stencil dispatcher/executor route. */
class GPUWgpu4kClipStencilPreparedFrameSmokeTest {
    @Test
    fun `public prepared clip producer and two consumers encode and release once per frame`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(
            backend != null,
            "wgpu4k native adapter unavailable; skipping prepared clip-stencil smoke",
        )
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = GPUDeviceGenerationID(capabilities.snapshotId.substringAfterLast('-').toLong())
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET.width, TARGET.height, "rgba8unorm"),
        )
        var targetClosesAfterSessionClose = -1L
        try {
            val firstReadbackId = GPUReadbackRequestID("readback.clip-stencil.first")
            val first = renderFrame(
                session = session,
                taskList = clipStencilTaskList(
                    frameId = GPUFrameID(12_201L),
                    capabilities = capabilities,
                    generation = generation,
                    scenarioId = "first",
                    readbackId = firstReadbackId,
                    clipVertices = listOf(2f, 2f, 14f, 2f, 14f, 14f, 2f, 14f),
                    first = SmokeDraw(GPURect(1f, 1f, 10f, 10f), SmokeColor(255, 0, 0, 128)),
                    second = SmokeDraw(GPURect(6f, 6f, 15f, 15f), SmokeColor(0, 255, 0, 128)),
                ),
                readbackId = firstReadbackId,
            )
            assertPixel(first, 4, 4, 128, 0, 0, 128)
            assertPixel(first, 8, 8, 64, 128, 0, 192)
            assertPixel(first, 12, 12, 0, 128, 0, 128)
            assertPixel(first, 0, 0, 0, 0, 0, 0)
            assertPixel(first, 15, 15, 0, 0, 0, 0)

            val secondReadbackId = GPUReadbackRequestID("readback.clip-stencil.second-reset")
            val second = renderFrame(
                session = session,
                taskList = clipStencilTaskList(
                    frameId = GPUFrameID(12_202L),
                    capabilities = capabilities,
                    generation = generation,
                    scenarioId = "second-reset",
                    readbackId = secondReadbackId,
                    clipVertices = listOf(7f, 7f, 14f, 7f, 14f, 14f, 7f, 14f),
                    first = SmokeDraw(GPURect(1f, 1f, 10f, 10f), SmokeColor(0, 0, 255, 128)),
                    second = SmokeDraw(GPURect(6f, 6f, 15f, 15f), SmokeColor(255, 255, 0, 128)),
                ),
                readbackId = secondReadbackId,
            )
            assertPixel(second, 4, 4, 0, 0, 0, 0)
            assertPixel(second, 6, 6, 0, 0, 0, 0)
            assertPixel(second, 8, 8, 128, 128, 64, 192)
            assertPixel(second, 12, 12, 128, 128, 0, 128)
            assertPixel(second, 0, 0, 0, 0, 0, 0)
            assertPixel(second, 15, 15, 0, 0, 0, 0)

            val inverseReadbackId = GPUReadbackRequestID(
                "readback.clip-stencil.even-odd-inverse-concave-hole",
            )
            val evenOddInverse = renderFrame(
                session = session,
                taskList = clipStencilTaskList(
                    frameId = GPUFrameID(12_203L),
                    capabilities = capabilities,
                    generation = generation,
                    scenarioId = "even-odd-inverse-concave-hole",
                    readbackId = inverseReadbackId,
                    clipVertices = listOf(
                        3f, 3f, 13f, 3f, 13f, 6f, 8f, 6f, 8f, 13f, 3f, 13f,
                        4f, 7f, 7f, 7f, 7f, 10f, 4f, 10f,
                    ),
                    clipContourStarts = listOf(0, 6),
                    fillRule = GPUClipFillRule.EvenOdd,
                    inverseFill = true,
                    scissor = GPUPixelBounds(1, 1, 15, 15),
                    first = SmokeDraw(
                        GPURect(0f, 0f, 16f, 16f),
                        SmokeColor(255, 0, 0, 128),
                    ),
                    second = SmokeDraw(
                        GPURect(0f, 0f, 16f, 16f),
                        SmokeColor(0, 255, 0, 128),
                    ),
                ),
                readbackId = inverseReadbackId,
            )
            assertPixel(evenOddInverse, 2, 2, 64, 128, 0, 192)
            assertPixel(evenOddInverse, 4, 4, 0, 0, 0, 0)
            assertPixel(evenOddInverse, 5, 8, 64, 128, 0, 192)
            assertPixel(evenOddInverse, 10, 10, 64, 128, 0, 192)
            assertPixel(evenOddInverse, 0, 0, 0, 0, 0, 0)
            assertPixel(evenOddInverse, 15, 15, 0, 0, 0, 0)

            val counters = session.nativeCounters()
            assertEquals(3L, counters.encoders)
            assertEquals(3L, counters.commandBuffers)
            assertEquals(3L, counters.submits)
            assertEquals(3L, counters.readbackCopies)
            assertEquals(3L, counters.retentionRegistrations)
            assertEquals(3L, counters.retentionCompletions)
            assertEquals(0L, counters.retentionQuarantines)
            assertEquals(0, counters.activeNativePayloads)
            assertEquals(0, counters.outputOwnedNativePayloads)
            assertEquals(0, counters.quarantinedNativePayloads)
        } finally {
            try {
                session.close()
                targetClosesAfterSessionClose = session.nativeCounters().targetCloses
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
        assertEquals(1L, targetClosesAfterSessionClose)
    }

    private fun renderFrame(
        session: GPUPreparedSceneFrameSession,
        taskList: GPUTaskList,
        readbackId: GPUReadbackRequestID,
    ): ByteArray {
        val renderSteps = GPUFramePlanner.plan(taskList).steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        assertEquals(
            listOf(
                GPUDrawPacketRole.StencilProducer,
                GPUDrawPacketRole.Shading,
                GPUDrawPacketRole.Shading,
            ),
            renderSteps.map { step -> step.drawPackets.single().role },
        )

        val nativeBefore = session.nativeCounters()
        val renderBefore = session.renderCounters()
        val terminal = session.renderFrame(
            taskList,
            GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
        ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
        assertEquals(
            GPUFrameStructuralOutcome.Succeeded,
            terminal.outcome,
            "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
        )
        val nativeAfter = session.nativeCounters()
        val renderAfter = session.renderCounters()
        assertEquals(1L, nativeAfter.encoders - nativeBefore.encoders)
        assertEquals(1L, nativeAfter.commandBuffers - nativeBefore.commandBuffers)
        assertEquals(1L, nativeAfter.submits - nativeBefore.submits)
        assertEquals(1L, nativeAfter.readbackCopies - nativeBefore.readbackCopies)
        assertEquals(1L, nativeAfter.retentionRegistrations - nativeBefore.retentionRegistrations)
        assertEquals(1L, nativeAfter.retentionCompletions - nativeBefore.retentionCompletions)
        assertEquals(0L, nativeAfter.retentionQuarantines - nativeBefore.retentionQuarantines)
        assertEquals(3L, renderAfter.renderPasses - renderBefore.renderPasses)
        assertEquals(3L, renderAfter.drawIndexed - renderBefore.drawIndexed)
        assertEquals(0, nativeAfter.activeNativePayloads)
        return assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
    }

    private fun clipStencilTaskList(
        frameId: GPUFrameID,
        capabilities: GPUCapabilities,
        generation: GPUDeviceGenerationID,
        scenarioId: String,
        readbackId: GPUReadbackRequestID,
        clipVertices: List<Float>,
        clipContourStarts: List<Int> = listOf(0),
        fillRule: GPUClipFillRule = GPUClipFillRule.Winding,
        inverseFill: Boolean = false,
        scissor: GPUPixelBounds = TARGET,
        first: SmokeDraw,
        second: SmokeDraw,
    ): GPUTaskList {
        val draws = listOf(first, second)
        val commands = draws.mapIndexed { index, draw ->
            GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(1_000 + (frameId.value - 12_200L).toInt() * 10 + index),
                rect = draw.rect,
                target = GPUTargetFacts(TARGET.width, TARGET.height, "rgba8unorm"),
                material = GPUMaterialDescriptor.SolidColor(
                    draw.color.red / 255f,
                    draw.color.green / 255f,
                    draw.color.blue / 255f,
                    draw.color.alpha / 255f,
                ),
                clip = GPUClipFacts(
                    kind = GPUClipKind.WideOpen,
                    bounds = GPUBounds(0f, 0f, TARGET.width.toFloat(), TARGET.height.toFloat()),
                    coveragePlan = GPUClipCoveragePlan.NoClip,
                ),
                paintOrder = index,
                source = GPUCommandSource("unit-test", "clip-stencil-smoke", GPUFrameProvenance.GmContent),
            ).copy(antiAlias = false)
        }
        val clipPlan = nativeClipStencilPlan(
            scenarioId,
            clipVertices,
            clipContourStarts,
            fillRule,
            inverseFill,
            scissor,
        )
        val base = GPURecorder(
            GPURecordingID("recording.clip-stencil.$scenarioId"),
            frameId,
            capabilities,
            generation,
        ).apply {
            commands.forEach(::record)
        }.close().taskList.withClipPlans(commands.associate { command -> command.commandId.value to clipPlan })
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        assertEquals(2, packets.size, "Clip-stencil base recording refused: ${base.diagnostics}")
        val commandsById = commands.associateBy { command -> command.commandId.value }
        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            GPUCorePrimitivePreparedFrameRequest(
                baseTaskList = base,
                capabilities = capabilities,
                target = GPUFrameTargetRef(TARGET_ID),
                targetBounds = TARGET,
                semanticsByCommandId = packets.associate { packet ->
                    packet.commandIdValue to requireNotNull(commandsById[packet.commandIdValue])
                        .semantic(packet, scissor)
                },
                readbackRequestId = readbackId,
            ),
        )
        return assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            result,
            (result as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
    }

    private fun org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.FillRect.semantic(
        packet: GPUDrawPacket,
        scissor: GPUPixelBounds,
    ) = GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = commandId.value,
            sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
            geometry = GPUCorePrimitiveGeometryInput.Rect(rect.left, rect.top, rect.right, rect.bottom),
            premultipliedRgba = (material as GPUMaterialDescriptor.SolidColor).let { color ->
                listOf(color.r * color.a, color.g * color.a, color.b * color.a, color.a)
            },
            targetBounds = TARGET,
            scissorBounds = scissor,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
            frameProvenance = GPUFrameProvenance.GmContent,
            coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
            analysisRecordId = "analysis.fill_rect.${commandId.value}",
            analysisCommandFamily = "FillRect",
            rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
            rectGeometryAuthority = corePrimitiveRectGeometryAuthority(rect, GPUTransformFacts.identity()),
        ),
    )

    private fun nativeClipStencilPlan(
        scenarioId: String,
        vertices: List<Float>,
        contourStarts: List<Int>,
        fillRule: GPUClipFillRule,
        inverseFill: Boolean,
        scissor: GPUPixelBounds,
    ) = GPUClipExecutionPlan.StencilCoverage(
        contentKey = "clip.native.smoke.$scenarioId",
        bounds = TARGET,
        sampleCount = 1,
        atomicGroup = GPUClipAtomicGroupID("atomic.clip.native.smoke.$scenarioId"),
        orderingToken = GPUClipOrderingToken("token.clip.native.smoke.$scenarioId"),
        producer = GPUClipStencilProducerPlan(
            geometry = GPUClipExecutionGeometry.Path(
                vertices = vertices,
                contourStarts = contourStarts,
                fillRule = fillRule,
                inverseFill = inverseFill,
            ),
            scissor = scissor,
            fillRule = fillRule,
            reference = 0u,
            compare = GPUClipStencilCompare.Always,
            frontPassOperation = if (fillRule == GPUClipFillRule.Winding) {
                GPUClipStencilOperation.IncrementWrap
            } else {
                GPUClipStencilOperation.Invert
            },
            backPassOperation = if (fillRule == GPUClipFillRule.Winding) {
                GPUClipStencilOperation.DecrementWrap
            } else {
                GPUClipStencilOperation.Invert
            },
            loadOperation = GPUClipStencilLoadOperation.Clear,
            storeOperation = GPUClipStencilStoreOperation.Store,
            clearValue = 0u,
        ),
        consumer = GPUClipStencilConsumerPlan(
            scissor = scissor,
            reference = 0u,
            compare = if (inverseFill) GPUClipStencilCompare.Equal else GPUClipStencilCompare.NotEqual,
        ),
    )

    private fun assertPixel(
        bytes: ByteArray,
        x: Int,
        y: Int,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        val offset = (y * TARGET.width + x) * 4
        assertEquals(
            listOf(red, green, blue, alpha),
            (0..3).map { channel -> bytes[offset + channel].toInt() and 0xff },
            "pixel ($x,$y)",
        )
    }

    private fun GPUTaskList.withClipPlans(plans: Map<Int, GPUClipExecutionPlan>): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val packets = task.drawPackets.map { packet ->
                packet.withClipPlan(requireNotNull(plans[packet.commandIdValue]))
            }
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
                packets.associate { packet ->
                    packet.packetId to requireNotNull(task.batchEligibilityByPacketId[packet.packetId])
                },
                task.sampleContinuationKey,
                task.compositeMembership,
            )
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun GPUDrawPacket.withClipPlan(plan: GPUClipExecutionPlan): GPUDrawPacket = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role,
        blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot,
        semanticPayload, vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder,
        resourceGeneration, frameProvenance, clipCoveragePlan, plan, diagnostics,
    )

    private data class SmokeDraw(
        val rect: GPURect,
        val color: SmokeColor,
    )

    private data class SmokeColor(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
    ) {
        init {
            assertTrue(listOf(red, green, blue, alpha).all { component -> component in 0..255 })
        }
    }

    private companion object {
        const val TARGET_ID = "target.clip-stencil.smoke"
        val TARGET = GPUPixelBounds(0, 0, 16, 16)
    }
}
