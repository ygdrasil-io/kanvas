package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
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
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
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
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
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
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

/** Native public-wgpu4k proof for the bounded Clip StencilAA MSAA 4x continuation. */
class GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest {
    @Test
    fun `native clip stencil AA 4x retains color and stencil across three passes and reuses its pair`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "wgpu4k native adapter unavailable; skipping clip stencil AA 4x smoke")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val rgbaSupport = capabilities.textureFormatSampleSupport[GPUTextureFormat.RGBA8Unorm]
        val depthSupport = capabilities.textureFormatSampleSupport[GPUTextureFormat.Depth24PlusStencil8]
        assumeTrue(
            rgbaSupport != null &&
                4 in rgbaSupport.renderAttachmentSampleCounts &&
                4 in rgbaSupport.resolveSourceSampleCounts &&
                depthSupport != null && 4 in depthSupport.renderAttachmentSampleCounts,
            "rgba8unorm resolve plus depth24plus-stencil8 4x unavailable; skipping clip stencil AA smoke",
        )
        val generation = GPUDeviceGenerationID(capabilities.snapshotId.substringAfterLast('-').toLong())
        val targetRef = GPUFrameTargetRef(TARGET_ID)
        val readbackId = GPUReadbackRequestID("readback.clip-stencil-aa-4x")
        val taskList = clipStencilAaTaskList(capabilities, generation, targetRef, readbackId)
        val renderSteps = GPUFramePlanner.plan(taskList).steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        assertEquals(
            listOf(GPUDrawPacketRole.StencilProducer, GPUDrawPacketRole.Shading, GPUDrawPacketRole.Shading),
            renderSteps.map { it.drawPackets.single().role },
        )
        assertTrue(renderSteps.all { it.samplePlan == GPUSamplePlan.MultisampleFrame(4) })
        assertEquals(1, renderSteps.map { requireNotNull(it.sampleContinuation).key }.distinct().size)
        assertTrue(renderSteps.all { requireNotNull(it.sampleContinuation).key.depthStencilAttachment != null })

        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(TARGET.width, TARGET.height, "rgba8unorm"),
        )
        try {
            val nativeBefore = session.nativeCounters()
            val renderBefore = session.renderCounters()
            val first = render(session, taskList, readbackId)
            assertPixel(first, 6, 6, 255, 0, 0, 255)
            assertPixel(first, 12, 12, 127, 128, 0, 255)
            assertPixel(first, 28, 28, 0, 0, 0, 0)
            assertPartialPremultipliedRedEdge(first)

            val nativeAfterFirst = session.nativeCounters()
            val renderAfterFirst = session.renderCounters()
            assertFrameCounters(nativeBefore, nativeAfterFirst, renderBefore, renderAfterFirst)
            assertEquals(1L, renderAfterFirst.msaaColorTextureCreations - renderBefore.msaaColorTextureCreations)
            assertEquals(0L, renderAfterFirst.msaaColorSlotReuses - renderBefore.msaaColorSlotReuses)
            assertEquals(
                1L,
                renderAfterFirst.clipDepthStencilTextureCreations - renderBefore.clipDepthStencilTextureCreations,
            )
            assertEquals(
                0L,
                renderAfterFirst.clipDepthStencilSlotReuses - renderBefore.clipDepthStencilSlotReuses,
            )
            assertEquals(2L, nativeAfterFirst.corePrimitiveInvariantCreations - nativeBefore.corePrimitiveInvariantCreations)

            val second = render(session, taskList, readbackId)
            assertPixel(second, 6, 6, 255, 0, 0, 255)
            assertPixel(second, 12, 12, 127, 128, 0, 255)
            assertPartialPremultipliedRedEdge(second)

            val nativeAfterSecond = session.nativeCounters()
            val renderAfterSecond = session.renderCounters()
            assertFrameCounters(nativeAfterFirst, nativeAfterSecond, renderAfterFirst, renderAfterSecond)
            assertEquals(
                0L,
                renderAfterSecond.msaaColorTextureCreations - renderAfterFirst.msaaColorTextureCreations,
            )
            assertEquals(1L, renderAfterSecond.msaaColorSlotReuses - renderAfterFirst.msaaColorSlotReuses)
            assertEquals(
                0L,
                renderAfterSecond.clipDepthStencilTextureCreations -
                    renderAfterFirst.clipDepthStencilTextureCreations,
            )
            assertEquals(
                1L,
                renderAfterSecond.clipDepthStencilSlotReuses - renderAfterFirst.clipDepthStencilSlotReuses,
            )
            assertEquals(
                0L,
                nativeAfterSecond.corePrimitiveInvariantCreations - nativeAfterFirst.corePrimitiveInvariantCreations,
            )
            assertTrue(
                nativeAfterSecond.corePrimitiveInvariantReuses - nativeAfterFirst.corePrimitiveInvariantReuses >= 2L,
            )
            assertEquals(0L, nativeAfterSecond.retentionQuarantines - nativeBefore.retentionQuarantines)
            assertEquals(0, nativeAfterSecond.activeNativePayloads)
            assertEquals(0, nativeAfterSecond.outputOwnedNativePayloads)
            assertEquals(0, nativeAfterSecond.quarantinedNativePayloads)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    private fun render(
        session: GPUPreparedSceneFrameSession,
        taskList: GPUTaskList,
        readbackId: GPUReadbackRequestID,
    ): ByteArray {
        val terminal = session.renderFrame(
            taskList,
            GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
        ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
        assertEquals(
            GPUFrameStructuralOutcome.Succeeded,
            terminal.outcome,
            "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
        )
        return assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
    }

    private fun assertFrameCounters(
        nativeBefore: GPUPreparedSceneNativeCounters,
        nativeAfter: GPUPreparedSceneNativeCounters,
        renderBefore: GPUPreparedSceneRenderCounters,
        renderAfter: GPUPreparedSceneRenderCounters,
    ) {
        assertEquals(1L, nativeAfter.encoders - nativeBefore.encoders)
        assertEquals(1L, nativeAfter.commandBuffers - nativeBefore.commandBuffers)
        assertEquals(1L, nativeAfter.submits - nativeBefore.submits)
        assertEquals(1L, nativeAfter.readbackCopies - nativeBefore.readbackCopies)
        assertEquals(3L, renderAfter.renderPasses - renderBefore.renderPasses)
        assertEquals(3L, renderAfter.drawIndexed - renderBefore.drawIndexed)
    }

    private fun clipStencilAaTaskList(
        capabilities: GPUCapabilities,
        generation: GPUDeviceGenerationID,
        targetRef: GPUFrameTargetRef,
        readbackId: GPUReadbackRequestID,
    ): GPUTaskList {
        val draws = listOf(
            SmokeDraw(GPURect(1f, 1f, 31f, 31f), SmokeColor(255, 0, 0, 255)),
            SmokeDraw(GPURect(8f, 8f, 24f, 24f), SmokeColor(0, 255, 0, 128)),
        )
        val commands = draws.mapIndexed { index, draw ->
            GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(12_600 + index),
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
                source = GPUCommandSource("unit-test", "clip-stencil-aa-4x", GPUFrameProvenance.GmContent),
            ).copy(antiAlias = false)
        }
        val clipPlan = GPUClipExecutionPlan.StencilCoverage(
            contentKey = "clip.native.smoke.aa-4x",
            bounds = TARGET,
            sampleCount = 4,
            atomicGroup = GPUClipAtomicGroupID("atomic.clip.native.smoke.aa-4x"),
            orderingToken = GPUClipOrderingToken("token.clip.native.smoke.aa-4x"),
            producer = GPUClipStencilProducerPlan(
                geometry = GPUClipExecutionGeometry.Path(
                    vertices = listOf(4.25f, 4.25f, 27.75f, 4.25f, 4.25f, 27.75f),
                    contourStarts = listOf(0),
                    fillRule = GPUClipFillRule.Winding,
                    inverseFill = false,
                ),
                scissor = TARGET,
                fillRule = GPUClipFillRule.Winding,
                reference = 0u,
                compare = GPUClipStencilCompare.Always,
                frontPassOperation = GPUClipStencilOperation.IncrementWrap,
                backPassOperation = GPUClipStencilOperation.DecrementWrap,
                loadOperation = GPUClipStencilLoadOperation.Clear,
                storeOperation = GPUClipStencilStoreOperation.Store,
                clearValue = 0u,
            ),
            consumer = GPUClipStencilConsumerPlan(
                scissor = TARGET,
                reference = 0u,
                compare = GPUClipStencilCompare.NotEqual,
            ),
        )
        val base = GPURecorder(
            GPURecordingID("recording.clip-stencil-aa-4x"),
            GPUFrameID(12_600L),
            capabilities,
            generation,
        ).apply {
            commands.forEach(::record)
        }.close().taskList
            .withClipPlans(commands.associate { it.commandId.value to clipPlan })
            .withMsaa4x(targetRef, generation)
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        assertEquals(2, packets.size, "Clip-stencil AA base recording refused: ${base.diagnostics}")
        val commandsById = commands.associateBy { it.commandId.value }
        val result = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            GPUCorePrimitivePreparedFrameRequest(
                baseTaskList = base,
                capabilities = capabilities,
                target = targetRef,
                targetBounds = TARGET,
                semanticsByCommandId = packets.associate { packet ->
                    packet.commandIdValue to requireNotNull(commandsById[packet.commandIdValue]).semantic(packet)
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

    private fun GPUTaskList.withMsaa4x(
        target: GPUFrameTargetRef,
        generation: GPUDeviceGenerationID,
    ): GPUTaskList {
        val samplePlan = GPUSamplePlan.MultisampleFrame(4)
        val continuationKey = GPUSampleContinuationKey(
            target = GPUTargetIdentity(target.value),
            targetGeneration = 1L,
            deviceGeneration = generation,
            colorFormat = GPUColorFormat("rgba8unorm"),
            colorInterpretation = GPUColorInterpretation("encoded-premul-srgb"),
            samplePlan = samplePlan,
            attachmentAuthority = GPUSampleAttachmentAuthority.PreparedFramePayload,
            colorAttachment = GPUTargetIdentity("msaa-color:${target.value}:1"),
            depthStencilAttachment = null,
        )
        return GPUTaskList(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = recordingSeals,
            expectedReplayKeyHash = expectedReplayKeyHash,
            tasks = tasks.map { task ->
                if (task !is GPUTask.Render) return@map task
                GPUTask.Render(
                    task.taskId,
                    task.recordingId,
                    task.phase,
                    task.target,
                    task.loadStore,
                    samplePlan,
                    task.resourceUses,
                    task.provisionalSegmentKey,
                    task.drawPackets,
                    task.batchEligibilityByPacketId,
                    continuationKey,
                    task.compositeMembership,
                    task.depthStencilLoadStore,
                )
            },
            dependencies = dependencies,
            phaseOrder = phaseOrder,
            memoryBudget = memoryBudget,
            diagnostics = diagnostics,
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
                task.depthStencilLoadStore,
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

    private fun assertPartialPremultipliedRedEdge(bytes: ByteArray) {
        val edge = listOf(26 to 5, 25 to 6, 24 to 7).firstNotNullOfOrNull { (x, y) ->
            val offset = (y * TARGET.width + x) * 4
            val rgba = (0..3).map { channel -> bytes[offset + channel].toInt() and 0xff }
            rgba.takeIf { it[3] in 1..254 && it[0] == it[3] && it[1] == 0 && it[2] == 0 }
        }
        assertTrue(edge != null, "diagonal 4x clip edge must contain one premultiplied partial red pixel")
    }

    private data class SmokeDraw(val rect: GPURect, val color: SmokeColor)

    private data class SmokeColor(val red: Int, val green: Int, val blue: Int, val alpha: Int)

    private companion object {
        const val TARGET_ID = "target.clip-stencil-aa-4x.smoke"
        val TARGET = GPUPixelBounds(0, 0, 32, 32)
    }
}
