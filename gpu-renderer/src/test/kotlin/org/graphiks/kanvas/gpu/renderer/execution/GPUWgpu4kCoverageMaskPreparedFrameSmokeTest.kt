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
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
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

/** Public-wgpu4k smoke for the prepared color-only coverage-mask route. */
class GPUWgpu4kCoverageMaskPreparedFrameSmokeTest {
    @Test
    fun `two prepared mask frames reset pixels reuse invariants and encode exact draws`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(
            backend != null,
            "wgpu4k native adapter unavailable; skipping prepared coverage-mask smoke",
        )
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = backend.deviceGeneration
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET.width, TARGET.height),
        )
        var targetClosesAfterSessionClose = -1L
        try {
            val firstReadbackId = GPUReadbackRequestID("readback.coverage-mask.first")
            val first = renderFrame(
                session,
                coverageMaskTaskList(
                    frameId = GPUFrameID(12_601L),
                    capabilities = capabilities,
                    generation = generation,
                    scenarioId = "first",
                    readbackId = firstReadbackId,
                    producers = listOf(
                        GPUClipMaskProducerPlan(
                            sourceOrder = 0,
                            geometry = GPUClipExecutionGeometry.Rect(
                                GPUClipBounds(4f, 4f, 28f, 28f),
                            ),
                            combine = GPUClipMaskCombine.Intersect,
                            antiAlias = false,
                        ),
                        GPUClipMaskProducerPlan(
                            sourceOrder = 1,
                            geometry = GPUClipExecutionGeometry.RRect(
                                GPUClipBounds(10f, 10f, 22f, 22f),
                                List(8) { 4f },
                            ),
                            combine = GPUClipMaskCombine.Difference,
                            antiAlias = false,
                        ),
                    ),
                ),
                firstReadbackId,
                producerCount = 2,
            )
            assertPixel(first.bytes, 2, 2, 0, 0, 0, 0)
            assertPixel(first.bytes, 6, 6, 128, 0, 0, 128)
            assertPixel(first.bytes, 24, 6, 0, 128, 0, 128)
            assertPixel(first.bytes, 16, 6, 64, 128, 0, 192)
            assertPixel(first.bytes, 10, 10, 128, 0, 0, 128)
            assertPixel(first.bytes, 16, 16, 0, 0, 0, 0)
            assertPixel(first.bytes, 29, 29, 0, 0, 0, 0)
            assertEquals(3L, first.nativeAfter.corePrimitiveInvariantCreations)
            assertEquals(
                1L,
                first.renderAfter.coverageMaskTextureCreations -
                    first.renderBefore.coverageMaskTextureCreations,
            )
            assertEquals(
                0L,
                first.renderAfter.coverageMaskSlotReuses -
                    first.renderBefore.coverageMaskSlotReuses,
            )

            val secondReadbackId = GPUReadbackRequestID("readback.coverage-mask.second-reset")
            val second = renderFrame(
                session,
                coverageMaskTaskList(
                    frameId = GPUFrameID(12_602L),
                    capabilities = capabilities,
                    generation = generation,
                    scenarioId = "second-reset",
                    readbackId = secondReadbackId,
                    producers = listOf(
                        GPUClipMaskProducerPlan(
                            sourceOrder = 0,
                            geometry = GPUClipExecutionGeometry.Rect(
                                GPUClipBounds(0f, 0f, 4f, 4f),
                            ),
                            combine = GPUClipMaskCombine.Intersect,
                            antiAlias = false,
                        ),
                    ),
                ),
                secondReadbackId,
                producerCount = 1,
            )
            assertPixel(second.bytes, 2, 2, 128, 0, 0, 128)
            assertPixel(second.bytes, 6, 6, 0, 0, 0, 0)
            assertEquals(
                0L,
                second.nativeAfter.corePrimitiveInvariantCreations -
                    second.nativeBefore.corePrimitiveInvariantCreations,
            )
            assertTrue(
                second.nativeAfter.corePrimitiveInvariantReuses -
                    second.nativeBefore.corePrimitiveInvariantReuses >= 2L,
            )
            assertEquals(
                0L,
                second.renderAfter.coverageMaskTextureCreations -
                    second.renderBefore.coverageMaskTextureCreations,
            )
            assertEquals(
                1L,
                second.renderAfter.coverageMaskSlotReuses -
                    second.renderBefore.coverageMaskSlotReuses,
            )

            val counters = session.nativeCounters()
            val renderCounters = session.renderCounters()
            assertEquals(1L, counters.targetCreations)
            assertEquals(0L, counters.targetCloses)
            assertEquals(2L, counters.targetNativeUses)
            assertEquals(2L, counters.encoders)
            assertEquals(2L, counters.commandBuffers)
            assertEquals(2L, counters.submits)
            assertEquals(2L, counters.readbackCopies)
            assertEquals(2L, counters.retentionRegistrations)
            assertEquals(2L, counters.retentionCompletions)
            assertEquals(0L, counters.retentionQuarantines)
            assertEquals(2L, counters.frameCoordinatorCreations)
            assertEquals(2L, counters.nativePayloadRegistrations)
            assertEquals(2, counters.distinctRetentionTickets)
            assertEquals(0, counters.activeNativePayloads)
            assertEquals(0, counters.outputOwnedNativePayloads)
            assertEquals(0, counters.quarantinedNativePayloads)
            assertEquals(7L, renderCounters.renderPasses)
            assertEquals(3L, renderCounters.draws)
            assertEquals(4L, renderCounters.drawIndexed)
            assertEquals(1L, renderCounters.coverageMaskTextureCreations)
            assertEquals(1L, renderCounters.coverageMaskSlotReuses)
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
        producerCount: Int,
    ): FrameEvidence {
        val framePlan = GPUFramePlanner.plan(taskList)
        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        assertEquals(
            List(producerCount) { GPUDrawPacketRole.ClipProducer } +
                List(2) { GPUDrawPacketRole.Shading },
            renderSteps.map { step -> step.drawPackets.single().role },
        )
        assertEquals(1, framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().size)

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
        val expectedPasses = producerCount + 2L
        assertEquals(1L, nativeAfter.encoders - nativeBefore.encoders)
        assertEquals(1L, nativeAfter.commandBuffers - nativeBefore.commandBuffers)
        assertEquals(1L, nativeAfter.submits - nativeBefore.submits)
        assertEquals(1L, nativeAfter.readbackCopies - nativeBefore.readbackCopies)
        assertEquals(1L, nativeAfter.retentionRegistrations - nativeBefore.retentionRegistrations)
        assertEquals(1L, nativeAfter.retentionCompletions - nativeBefore.retentionCompletions)
        assertEquals(0L, nativeAfter.retentionQuarantines - nativeBefore.retentionQuarantines)
        assertEquals(expectedPasses, renderAfter.renderPasses - renderBefore.renderPasses)
        assertEquals(producerCount.toLong(), renderAfter.draws - renderBefore.draws)
        assertEquals(2L, renderAfter.drawIndexed - renderBefore.drawIndexed)
        assertEquals(0, nativeAfter.activeNativePayloads)
        assertEquals(0, nativeAfter.outputOwnedNativePayloads)
        assertEquals(0, nativeAfter.quarantinedNativePayloads)
        return FrameEvidence(
            assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes,
            nativeBefore,
            nativeAfter,
            renderBefore,
            renderAfter,
        )
    }

    private fun coverageMaskTaskList(
        frameId: GPUFrameID,
        capabilities: GPUCapabilities,
        generation: GPUDeviceGenerationID,
        scenarioId: String,
        readbackId: GPUReadbackRequestID,
        producers: List<GPUClipMaskProducerPlan>,
    ): GPUTaskList {
        val draws = listOf(
            SmokeDraw(GPURect(0f, 0f, 20f, 32f), SmokeColor(255, 0, 0, 128)),
            SmokeDraw(GPURect(12f, 0f, 32f, 32f), SmokeColor(0, 255, 0, 128)),
        )
        val commands = draws.mapIndexed { index, draw ->
            GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(2_000 + (frameId.value - 12_600L).toInt() * 10 + index),
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
                source = GPUCommandSource("unit-test", "coverage-mask-smoke", GPUFrameProvenance.GmContent),
            ).copy(antiAlias = false)
        }
        val plan = GPUClipExecutionPlan.CoverageMask(
            contentKey = "clip.native.smoke.$scenarioId",
            bounds = TARGET,
            sampleCount = 1,
            depthStencilRequired = false,
            orderingToken = GPUClipOrderingToken("token.clip.native.smoke.$scenarioId"),
            producers = producers,
            consumer = GPUClipMaskConsumerPlan(),
        )
        val base = GPURecorder(
            GPURecordingID("recording.coverage-mask.$scenarioId"),
            frameId,
            capabilities,
            generation,
        ).apply {
            commands.forEach(::record)
        }.close().taskList.withClipPlans(commands.associate { command -> command.commandId.value to plan })
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        assertEquals(2, packets.size, "Coverage-mask base recording refused: ${base.diagnostics}")
        val commandsById = commands.associateBy { command -> command.commandId.value }
        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            GPUCorePrimitivePreparedFrameRequest(
                baseTaskList = base,
                capabilities = capabilities,
                target = GPUFrameTargetRef(TARGET_ID),
                targetBounds = TARGET,
                semanticsByCommandId = packets.associate { packet ->
                    packet.commandIdValue to requireNotNull(commandsById[packet.commandIdValue])
                        .semantic(packet)
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
    ) = GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = commandId.value,
            sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
            geometry = GPUCorePrimitiveGeometryInput.Rect(rect.left, rect.top, rect.right, rect.bottom),
            premultipliedRgba = (material as GPUMaterialDescriptor.SolidColor).let { color ->
                listOf(color.r * color.a, color.g * color.a, color.b * color.a, color.a)
            },
            targetBounds = TARGET,
            scissorBounds = TARGET,
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

    private data class FrameEvidence(
        val bytes: ByteArray,
        val nativeBefore: GPUPreparedSceneNativeCounters,
        val nativeAfter: GPUPreparedSceneNativeCounters,
        val renderBefore: GPUPreparedSceneRenderCounters,
        val renderAfter: GPUPreparedSceneRenderCounters,
    )

    private data class SmokeDraw(val rect: GPURect, val color: SmokeColor)

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
        const val TARGET_ID = "target.coverage-mask.smoke"
        val TARGET = GPUPixelBounds(0, 0, 32, 32)
    }
}
