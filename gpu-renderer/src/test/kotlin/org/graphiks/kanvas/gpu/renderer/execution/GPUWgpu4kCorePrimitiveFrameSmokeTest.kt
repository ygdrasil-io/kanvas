package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
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
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
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

class GPUWgpu4kCorePrimitiveFrameSmokeTest {
    @Test
    fun `native real rect and affine fill share one pass submit and readback`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null)
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = GPUDeviceGenerationID(capabilities.snapshotId.substringAfterLast('-').toLong())
        val frameId = GPUFrameID(12_031L)
        val readbackId = GPUReadbackRequestID("readback.core-primitive.rect-affine")
        val targetBounds = GPUPixelBounds(0, 0, 32, 32)
        val rectScissor = GPUPixelBounds(4, 5, 18, 19)
        val axisRect = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(301),
            rect = GPURect(2f, 2f, 20f, 20f),
            target = GPUTargetFacts(32, 32, "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 0.5f),
            clip = GPUClipFacts(
                kind = GPUClipKind.WideOpen,
                bounds = GPUBounds(0f, 0f, 32f, 32f),
                coveragePlan = GPUClipCoveragePlan.NoClip,
            ),
            paintOrder = 0,
            source = GPUCommandSource("unit-test", "fillRect", GPUFrameProvenance.GmContent),
        ).copy(antiAlias = false)
        val affineRect = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(302),
            rect = GPURect(6f, 6f, 12f, 12f),
            target = GPUTargetFacts(32, 32, "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(0f, 1f, 0f, 0.5f),
            transform = GPUTransformFacts.affine(
                scaleX = 1f,
                skewX = 0.5f,
                skewY = 0f,
                scaleY = 1f,
            ),
            clip = GPUClipFacts(
                kind = GPUClipKind.WideOpen,
                bounds = GPUBounds(0f, 0f, 32f, 32f),
                coveragePlan = GPUClipCoveragePlan.NoClip,
            ),
            paintOrder = 1,
            source = GPUCommandSource("unit-test", "fillRect", GPUFrameProvenance.GmContent),
        ).copy(antiAlias = false)
        val base = GPURecorder(
            GPURecordingID("recording.core.smoke"),
            frameId,
            capabilities,
            generation,
        ).apply {
            record(axisRect)
            record(affineRect)
        }.close().taskList.withClipPlans(
            mapOf(
                axisRect.commandId.value to GPUClipExecutionPlan.ScissorOnly(rectScissor),
                affineRect.commandId.value to GPUClipExecutionPlan.NoClip,
            ),
        )
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        assertEquals(2, packets.size, "Core smoke base recording refused: ${base.tasks} ${base.diagnostics}")
        val commands = mapOf(axisRect.commandId.value to axisRect, affineRect.commandId.value to affineRect)
        val semantics = packets.associate { packet ->
            val command = requireNotNull(commands[packet.commandIdValue])
            packet.commandIdValue to command.coreSemantic(
                packet,
                targetBounds,
                if (command === axisRect) rectScissor else targetBounds,
            )
        }
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = base,
                    capabilities = capabilities,
                    target = GPUFrameTargetRef("target.core.smoke"),
                    targetBounds = targetBounds,
                    semanticsByCommandId = semantics,
                    readbackRequestId = readbackId,
                ),
            ),
        ).taskList
        val preparedRender = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals(2, preparedRender.drawPackets.size)
        val framePlan = GPUFramePlanner.plan(taskList)
        assertEquals(1, framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().size)
        assertEquals(1, framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().size)
        val routes = preparedRender.drawPackets.map { packet ->
            val semantic = assertIs<org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive>(
                packet.semanticPayload,
            )
            assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
                validateCorePrimitiveDirectNativeRoute(
                    semantic,
                    org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveDirectClipAuthority(
                        requireNotNull(packet.clipExecutionPlan),
                        targetBounds,
                    ),
                    packet.blendPlan,
                    preparedRender.samplePlan,
                    "rgba8unorm",
                ),
            )
        }
        val arena = packCorePrimitiveFrameGeometry(routes)
        assertTrue(arena.slices[1].baseVertex > 0)
        assertTrue(arena.slices[1].firstIndex > 0)
        val session = backend.prepareSceneFrameSession(GPUOffscreenTargetRequest(32, 32, "rgba8unorm"))
        try {
            val terminal = session.renderFrame(
                taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
            )
            val bytes = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
            assertPixel(bytes, 32, 6, 8, 128, 0, 0, 128)
            assertPixel(bytes, 32, 13, 8, 64, 128, 0, 191)
            assertPixel(bytes, 32, 3, 8, 0, 0, 0, 0)
            assertPixel(bytes, 32, 13, 23, 0, 0, 0, 0)
            val counters = session.nativeCounters()
            assertEquals(1L, counters.encoders)
            assertEquals(1L, counters.commandBuffers)
            assertEquals(1L, counters.submits)
            assertEquals(1L, counters.readbackCopies)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    private fun assertPixel(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        val offset = (y * width + x) * 4
        assertEquals(listOf(red, green, blue, alpha), (0..3).map { bytes[offset + it].toInt() and 0xff })
    }

    private fun org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.FillRect.coreSemantic(
        packet: GPUDrawPacket,
        targetBounds: GPUPixelBounds,
        scissorBounds: GPUPixelBounds,
    ) = GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = commandId.value,
            sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
            geometry = deviceGeometry(targetBounds),
            premultipliedRgba = (material as GPUMaterialDescriptor.SolidColor).let { color ->
                listOf(color.r * color.a, color.g * color.a, color.b * color.a, color.a)
            },
            targetBounds = targetBounds,
            scissorBounds = scissorBounds,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
            frameProvenance = GPUFrameProvenance.GmContent,
            coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
            analysisRecordId = "analysis.fill_rect.${commandId.value}",
            analysisCommandFamily = "FillRect",
            rectRouteAuthority = if (transform.skewX == 0f && transform.skewY == 0f) {
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned
            } else {
                GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1
            },
            rectGeometryAuthority = corePrimitiveRectGeometryAuthority(rect, transform),
        ),
    )

    private fun org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.FillRect.deviceGeometry(
        targetBounds: GPUPixelBounds,
    ): GPUCorePrimitiveGeometryInput {
        val corners = listOf(
            mapped(rect.left, rect.top),
            mapped(rect.right, rect.top),
            mapped(rect.right, rect.bottom),
            mapped(rect.left, rect.bottom),
        )
        return if (transform.skewX == 0f && transform.skewY == 0f) {
            GPUCorePrimitiveGeometryInput.Rect(
                corners.minOf { it.first },
                corners.minOf { it.second },
                corners.maxOf { it.first },
                corners.maxOf { it.second },
            )
        } else {
            GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = corners.flatMap { listOf(it.first, it.second) },
                indices = listOf(0, 1, 2, 0, 2, 3),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 4,
                coverBounds = corners.toPixelCoverBounds(targetBounds),
                geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
            )
        }
    }

    private fun List<Pair<Float, Float>>.toPixelCoverBounds(target: GPUPixelBounds): GPUPixelBounds =
        GPUPixelBounds(
            floor(minOf { it.first }).toInt().coerceIn(target.left, target.right),
            floor(minOf { it.second }).toInt().coerceIn(target.top, target.bottom),
            ceil(maxOf { it.first }).toInt().coerceIn(target.left, target.right),
            ceil(maxOf { it.second }).toInt().coerceIn(target.top, target.bottom),
        )

    private fun org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.FillRect.mapped(
        x: Float,
        y: Float,
    ): Pair<Float, Float> = Pair(
        transform.scaleX * x + transform.skewX * y + transform.translateX,
        transform.skewY * x + transform.scaleY * y + transform.translateY,
    )

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
}
