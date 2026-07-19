package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute

import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.gpu.renderer.analysis.GPUCorePrimitiveRRectGeometryAuthorityIssue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAnalyticElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds as GPUClipBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizationResult
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizer
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.sealedDeviceGeometryInput
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
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
                org.graphiks.kanvas.gpu.renderer.recording.classifyCorePrimitiveDirectNativeRoute(
                    semantic,
                    requireNotNull(packet.clipExecutionPlan),
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

    @Test
    fun `native analytic shape uniform80 proves rect aa asymmetric rrect pixels batching and reuse`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(
            backend != null,
            "wgpu4k native adapter unavailable; skipping analytic-shape uniform80 pixel proof",
        )
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = GPUDeviceGenerationID(capabilities.snapshotId.substringAfterLast('-').toLong())
        val session = backend.prepareSceneFrameSession(GPUOffscreenTargetRequest(32, 32, "rgba8unorm"))
        try {
            val firstFrame = buildAnalyticShapeSmokeFrame(
                capabilities = capabilities,
                generation = generation,
                frameValue = 12_048L,
                scenarioId = "analytic-shape-uniform80-first",
                commandIdBase = 800,
            )
            val preparedRender = firstFrame.taskList.tasks.filterIsInstance<GPUTask.Render>().single()
            val authorities = preparedRender.drawPackets.map { packet ->
                requireNotNull(packet.corePrimitivePreparedAuthority)
            }
            val structuralKey = authorities.first().structuralPipelineKey
            val seals = authorities.map { authority ->
                requireNotNull(authority.analyticShapeUniformSeal)
            }

            assertEquals(3, preparedRender.drawPackets.size)
            assertEquals(1, authorities.map { it.structuralPipelineKey }.distinct().size)
            assertEquals(
                GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape,
                structuralKey.shader,
            )
            assertEquals(
                GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.AnalyticShapeUniform80V1,
                structuralKey.uniformLayout,
            )
            val mapped = assertIs<GPUWgpu4kCorePrimitivePipelineMapping.Mapped>(
                mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structuralKey),
            )
            assertEquals(GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeSrcOver, mapped.identity.program)
            assertTrue(seals.all { seal -> seal.plan === seals.first().plan })
            assertEquals(listOf(0L, 256L, 512L), seals.map { seal -> seal.alignedOffset })
            assertTrue(seals.all { seal -> seal.payloadBytes == 80L })
            assertEquals(
                listOf(
                    GPUCorePrimitiveSourceFamily.Rect,
                    GPUCorePrimitiveSourceFamily.RRect,
                    GPUCorePrimitiveSourceFamily.RRect,
                ),
                preparedRender.drawPackets.map { packet ->
                    assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload).sourceFamily
                },
            )
            assertEquals(
                listOf(
                    GPUCorePrimitiveCoverageMode.ScalarAA,
                    GPUCorePrimitiveCoverageMode.FullOrScissor,
                    GPUCorePrimitiveCoverageMode.ScalarAA,
                ),
                preparedRender.drawPackets.map { packet ->
                    assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload).coverageMode
                },
            )
            assertTrue(preparedRender.drawPackets.all { packet ->
                packet.clipExecutionPlan == GPUClipExecutionPlan.NoClip
            })
            preparedRender.drawPackets.forEach { packet ->
                val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
                val route = assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
                    org.graphiks.kanvas.gpu.renderer.recording.classifyCorePrimitiveDirectNativeRoute(
                        semantic,
                        requireNotNull(packet.clipExecutionPlan),
                        packet.blendPlan,
                        preparedRender.samplePlan,
                        "rgba8unorm",
                    ),
                )
                assertEquals(GPUCorePrimitiveDirectNativeRoute.Lane.AnalyticShape, route.lane)
            }

            val framePlan = GPUFramePlanner.plan(firstFrame.taskList)
            assertEquals(1, framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().size)
            assertEquals(1, framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().size)
            assertTrue(
                framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                    .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                    .none { request ->
                        request.role in setOf(
                            GPUFrameResourceRole.ClipMask,
                            GPUFrameResourceRole.ClipDepthStencil,
                            GPUFrameResourceRole.PathDepthStencil,
                        )
                    },
                "AnalyticShapeSrcOver must not allocate a fallback clip, path, or mask attachment",
            )

            val nativeBefore = session.nativeCounters()
            val renderBefore = session.renderCounters()
            val firstTerminal = session.renderFrame(
                firstFrame.taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(firstFrame.readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                firstTerminal.outcome,
                "${firstTerminal.diagnostic?.code?.value}: ${firstTerminal.diagnostic?.message}",
            )
            assertEquals(null, firstTerminal.diagnostic)
            val pixels = assertIs<GPUSceneFrameOutput.ReadbackRgba>(firstTerminal.output).bytes

            assertPixel(pixels, 32, 4, 6, 255, 0, 0, 255)
            // Pixel center is 0.25 px inside the edge: coverage = 0.25 + 0.5 = 0.75.
            assertPixelChannelIn(pixels, 32, 1, 6, channel = 0, expected = 190..192)
            assertPixel(pixels, 32, 0, 6, 0, 0, 0, 0)

            assertPixel(pixels, 32, 16, 7, 0, 255, 0, 255)
            assertPixel(pixels, 32, 16, 2, 0, 255, 0, 255)
            assertPixel(pixels, 32, 11, 2, 0, 0, 0, 0)
            assertPixel(pixels, 32, 10, 7, 0, 0, 0, 0)

            assertPixel(pixels, 32, 19, 23, 0, 0, 255, 255)
            assertPixelChannelIn(pixels, 32, 19, 17, channel = 2, expected = 190..192)
            assertPixel(pixels, 32, 19, 16, 0, 0, 0, 0)

            val nativeAfterFirst = session.nativeCounters()
            val renderAfterFirst = session.renderCounters()
            assertEquals(1L, nativeAfterFirst.encoders - nativeBefore.encoders)
            assertEquals(1L, nativeAfterFirst.commandBuffers - nativeBefore.commandBuffers)
            assertEquals(1L, nativeAfterFirst.submits - nativeBefore.submits)
            assertEquals(1L, nativeAfterFirst.readbackCopies - nativeBefore.readbackCopies)
            assertEquals(
                1L,
                nativeAfterFirst.corePrimitiveInvariantCreations -
                    nativeBefore.corePrimitiveInvariantCreations,
            )
            assertEquals(
                0L,
                nativeAfterFirst.corePrimitiveInvariantInvalidations -
                    nativeBefore.corePrimitiveInvariantInvalidations,
            )
            assertEquals(1L, renderAfterFirst.renderPasses - renderBefore.renderPasses)
            assertEquals(3L, renderAfterFirst.drawIndexed - renderBefore.drawIndexed)

            val secondFrame = buildAnalyticShapeSmokeFrame(
                capabilities = capabilities,
                generation = generation,
                frameValue = 12_049L,
                scenarioId = "analytic-shape-uniform80-reuse",
                commandIdBase = 810,
            )
            val secondTerminal = session.renderFrame(
                secondFrame.taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(secondFrame.readbackId),
            ).completion.toCompletableFuture().get(15, TimeUnit.SECONDS)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                secondTerminal.outcome,
                "${secondTerminal.diagnostic?.code?.value}: ${secondTerminal.diagnostic?.message}",
            )
            assertEquals(null, secondTerminal.diagnostic)
            val secondPixels = assertIs<GPUSceneFrameOutput.ReadbackRgba>(secondTerminal.output).bytes
            assertPixel(secondPixels, 32, 4, 6, 255, 0, 0, 255)
            val nativeAfterSecond = session.nativeCounters()
            val renderAfterSecond = session.renderCounters()
            assertEquals(1L, nativeAfterSecond.encoders - nativeAfterFirst.encoders)
            assertEquals(1L, nativeAfterSecond.commandBuffers - nativeAfterFirst.commandBuffers)
            assertEquals(1L, nativeAfterSecond.submits - nativeAfterFirst.submits)
            assertEquals(1L, nativeAfterSecond.readbackCopies - nativeAfterFirst.readbackCopies)
            assertEquals(
                0L,
                nativeAfterSecond.corePrimitiveInvariantCreations -
                    nativeAfterFirst.corePrimitiveInvariantCreations,
            )
            assertEquals(
                1L,
                nativeAfterSecond.corePrimitiveInvariantReuses -
                    nativeAfterFirst.corePrimitiveInvariantReuses,
            )
            assertEquals(
                0L,
                nativeAfterSecond.corePrimitiveInvariantInvalidations -
                    nativeAfterFirst.corePrimitiveInvariantInvalidations,
            )
            assertEquals(1L, renderAfterSecond.renderPasses - renderAfterFirst.renderPasses)
            assertEquals(3L, renderAfterSecond.drawIndexed - renderAfterFirst.drawIndexed)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `native analytic rect and elliptical rrect pixels prove coverage y blend and batching`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(
            backend != null,
            "wgpu4k native adapter unavailable; skipping analytic clip pixel proofs",
        )
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = GPUDeviceGenerationID(capabilities.snapshotId.substringAfterLast('-').toLong())
        val session = backend.prepareSceneFrameSession(GPUOffscreenTargetRequest(32, 32, "rgba8unorm"))
        val ellipseRadii = listOf(6f, 3f, 6f, 3f, 6f, 3f, 6f, 3f)
        try {
            val fractionalHard = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_032L,
                "fractional-rect-hard-y",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticCoverage(
                            GPUClipExecutionGeometry.Rect(GPUClipBounds(3.25f, 4.25f, 13.75f, 10.75f)),
                            scissor = null,
                            antiAlias = false,
                        ),
                        SmokeColor(255, 32, 16),
                    ),
                ),
            )
            assertPixel(fractionalHard, 32, 4, 5, 255, 32, 16, 255)
            assertPixel(fractionalHard, 32, 14, 5, 0, 0, 0, 0)
            assertPixel(fractionalHard, 32, 4, 11, 0, 0, 0, 0)
            assertPixel(fractionalHard, 32, 4, 20, 0, 0, 0, 0)

            val rectAa = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_033L,
                "fractional-rect-aa",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticCoverage(
                            GPUClipExecutionGeometry.Rect(GPUClipBounds(4.25f, 4.25f, 12.75f, 12.75f)),
                            scissor = null,
                            antiAlias = true,
                        ),
                        SmokeColor(255, 0, 0),
                    ),
                ),
            )
            assertPixel(rectAa, 32, 5, 8, 255, 0, 0, 255)
            assertPartialPrimaryPixel(rectAa, 32, 4, 8, channel = 0)
            assertPixel(rectAa, 32, 3, 8, 0, 0, 0, 0)

            val rrectHard = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_034L,
                "elliptical-rrect-hard-scissor",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticCoverage(
                            GPUClipExecutionGeometry.RRect(
                                GPUClipBounds(4f, 4f, 20f, 20f),
                                ellipseRadii,
                            ),
                            scissor = GPUPixelBounds(4, 4, 20, 20),
                            antiAlias = false,
                        ),
                        SmokeColor(0, 255, 0),
                    ),
                ),
            )
            assertPixel(rrectHard, 32, 12, 4, 0, 255, 0, 255)
            assertPixel(rrectHard, 32, 12, 12, 0, 255, 0, 255)
            assertPixel(rrectHard, 32, 4, 4, 0, 0, 0, 0)

            val rrectAa = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_035L,
                "elliptical-rrect-aa",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticCoverage(
                            GPUClipExecutionGeometry.RRect(
                                GPUClipBounds(4f, 4f, 20f, 20f),
                                ellipseRadii,
                            ),
                            scissor = null,
                            antiAlias = true,
                        ),
                        SmokeColor(0, 0, 255),
                    ),
                ),
            )
            assertPixel(rrectAa, 32, 12, 12, 0, 0, 255, 255)
            assertPartialPrimaryPixel(rrectAa, 32, 4, 5, channel = 2)
            assertPixel(rrectAa, 32, 4, 4, 0, 0, 0, 0)

            val blendedBatch = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_036L,
                "two-values-premul-src-over",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticCoverage(
                            GPUClipExecutionGeometry.Rect(GPUClipBounds(0f, 0f, 32f, 32f)),
                            scissor = null,
                            antiAlias = false,
                        ),
                        SmokeColor(0, 0, 255, 128),
                    ),
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticCoverage(
                            GPUClipExecutionGeometry.Rect(GPUClipBounds(8f, 8f, 24f, 24f)),
                            scissor = GPUPixelBounds(6, 6, 26, 26),
                            antiAlias = false,
                        ),
                        SmokeColor(255, 0, 0, 128),
                    ),
                ),
            )
            assertPixel(blendedBatch, 32, 4, 4, 0, 0, 128, 128)
            assertPixel(blendedBatch, 32, 12, 12, 128, 0, 64, 192)
            assertPixel(blendedBatch, 32, 25, 12, 0, 0, 128, 128)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `native analytic intersections prove product coverage depth four y blend scissor and batching`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(
            backend != null,
            "wgpu4k native adapter unavailable; skipping analytic-intersection pixel proofs",
        )
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = GPUDeviceGenerationID(capabilities.snapshotId.substringAfterLast('-').toLong())
        val session = backend.prepareSceneFrameSession(GPUOffscreenTargetRequest(32, 32, "rgba8unorm"))
        fun rect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            antiAlias: Boolean,
        ) = GPUClipAnalyticElement(
            GPUClipExecutionGeometry.Rect(GPUClipBounds(left, top, right, bottom)),
            antiAlias,
        )
        fun rrect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            radiusX: Float,
            radiusY: Float,
            antiAlias: Boolean,
        ) = GPUClipAnalyticElement(
            GPUClipExecutionGeometry.RRect(
                GPUClipBounds(left, top, right, bottom),
                List(4) { listOf(radiusX, radiusY) }.flatten(),
            ),
            antiAlias,
        )
        try {
            val hardDepthTwo = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_037L,
                "intersection-hard-depth2-y-scissor",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticIntersection(
                            listOf(
                                rect(3f, 4f, 24f, 20f, antiAlias = false),
                                rect(8f, 7f, 20f, 15f, antiAlias = false),
                            ),
                        ),
                        SmokeColor(255, 96, 32),
                    ),
                ),
            )
            assertPixel(hardDepthTwo, 32, 10, 10, 255, 96, 32, 255)
            assertPixel(hardDepthTwo, 32, 5, 10, 0, 0, 0, 0)
            assertPixel(hardDepthTwo, 32, 10, 16, 0, 0, 0, 0)
            assertPixel(hardDepthTwo, 32, 10, 3, 0, 0, 0, 0)

            val mixedElliptic = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_038L,
                "intersection-rrect-rect-hard-aa",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticIntersection(
                            listOf(
                                rrect(4f, 4f, 24f, 20f, 5f, 3f, antiAlias = true),
                                rect(6f, 3f, 22f, 18f, antiAlias = false),
                            ),
                        ),
                        SmokeColor(0, 255, 0),
                    ),
                ),
            )
            assertPixel(mixedElliptic, 32, 12, 12, 0, 255, 0, 255)
            assertPartialPrimaryPixel(mixedElliptic, 32, 6, 4, channel = 1)
            assertPixel(mixedElliptic, 32, 5, 5, 0, 0, 0, 0)

            val depthFour = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_039L,
                "intersection-depth4",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticIntersection(
                            listOf(
                                rect(1f, 1f, 31f, 31f, antiAlias = false),
                                rect(3f, 2f, 29f, 30f, antiAlias = false),
                                rrect(5f, 4f, 27f, 28f, 2f, 2f, antiAlias = false),
                                rect(7f, 6f, 25f, 26f, antiAlias = false),
                            ),
                        ),
                        SmokeColor(32, 96, 255),
                    ),
                ),
            )
            assertPixel(depthFour, 32, 16, 16, 32, 96, 255, 255)
            assertPixel(depthFour, 32, 6, 16, 0, 0, 0, 0)

            val batchedDifferentStacks = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_040L,
                "intersection-two-stacks-premul-src-over",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticIntersection(
                            listOf(
                                rect(0f, 0f, 32f, 32f, antiAlias = false),
                                rrect(1f, 1f, 31f, 31f, 1f, 1f, antiAlias = false),
                            ),
                        ),
                        SmokeColor(0, 0, 255, 128),
                    ),
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticIntersection(
                            listOf(
                                rect(7f, 7f, 25f, 25f, antiAlias = false),
                                rrect(8f, 8f, 24f, 24f, 2f, 2f, antiAlias = true),
                                rect(8f, 8f, 24f, 24f, antiAlias = false),
                                rect(9f, 9f, 23f, 23f, antiAlias = false),
                            ),
                        ),
                        SmokeColor(255, 0, 0, 128),
                    ),
                ),
            )
            assertPixel(batchedDifferentStacks, 32, 4, 4, 0, 0, 128, 128)
            assertPixel(batchedDifferentStacks, 32, 16, 16, 128, 0, 64, 192)
            assertPixel(batchedDifferentStacks, 32, 26, 16, 0, 0, 128, 128)

            val productCoverage = renderAnalyticScenario(
                session,
                capabilities,
                generation,
                12_047L,
                "intersection-identical-aa-product",
                listOf(
                    AnalyticSmokeDraw(
                        GPUClipExecutionPlan.AnalyticIntersection(
                            listOf(
                                rect(4.25f, 4.25f, 20.75f, 20.75f, antiAlias = true),
                                rect(4.25f, 4.25f, 20.75f, 20.75f, antiAlias = true),
                            ),
                        ),
                        SmokeColor(255, 0, 0),
                    ),
                ),
            )
            assertPixelChannelIn(productCoverage, 32, 4, 10, channel = 0, expected = 140..146)
            assertPixel(productCoverage, 32, 5, 10, 255, 0, 0, 255)
            assertPixel(productCoverage, 32, 3, 10, 0, 0, 0, 0)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `native path stencil pixels prove fill rules inverse reset and device y`() {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(
            backend != null,
            "wgpu4k native adapter unavailable; skipping B3.2 path stencil pixel proofs",
        )
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = GPUDeviceGenerationID(capabilities.snapshotId.substringAfterLast('-').toLong())
        val session = backend.prepareSceneFrameSession(GPUOffscreenTargetRequest(32, 32, "rgba8unorm"))
        try {
            val geometry = stencilFan(
                contours = listOf(
                    listOf(
                        4f to 4f,
                        27f to 4f,
                        27f to 11f,
                        13f to 11f,
                        13f to 27f,
                        4f to 27f,
                    ),
                ),
                fillRule = GPUCorePrimitiveFillRule.Winding,
            )

            val bytes = renderScenario(
                session = session,
                capabilities = capabilities,
                generation = generation,
                frameValue = 12_041L,
                scenarioId = "winding-concave-y",
                draws = listOf(SmokeDraw.Path(geometry, SmokeColor(255, 64, 32))),
            )

            assertPixel(bytes, 32, 20, 7, 255, 64, 32, 255)
            assertPixel(bytes, 32, 7, 22, 255, 64, 32, 255)
            assertPixel(bytes, 32, 20, 22, 0, 0, 0, 0)
            assertPixel(bytes, 32, 2, 2, 0, 0, 0, 0)

            val windingHole = renderScenario(
                session = session,
                capabilities = capabilities,
                generation = generation,
                frameValue = 12_042L,
                scenarioId = "winding-hole-opposite",
                draws = listOf(
                    SmokeDraw.Path(
                        stencilFan(
                            contours = rectangularRingContours(holeOppositeOrientation = true),
                            fillRule = GPUCorePrimitiveFillRule.Winding,
                        ),
                        SmokeColor(32, 200, 80),
                    ),
                ),
            )
            assertPixel(windingHole, 32, 6, 16, 32, 200, 80, 255)
            assertPixel(windingHole, 32, 16, 16, 0, 0, 0, 0)
            assertPixel(windingHole, 32, 1, 1, 0, 0, 0, 0)

            val evenOddHole = renderScenario(
                session = session,
                capabilities = capabilities,
                generation = generation,
                frameValue = 12_043L,
                scenarioId = "even-odd-hole-same",
                draws = listOf(
                    SmokeDraw.Path(
                        stencilFan(
                            contours = rectangularRingContours(holeOppositeOrientation = false),
                            fillRule = GPUCorePrimitiveFillRule.EvenOdd,
                        ),
                        SmokeColor(48, 96, 240),
                    ),
                ),
            )
            assertPixel(evenOddHole, 32, 6, 16, 48, 96, 240, 255)
            assertPixel(evenOddHole, 32, 16, 16, 0, 0, 0, 0)
            assertPixel(evenOddHole, 32, 1, 1, 0, 0, 0, 0)

            val inverseWinding = renderScenario(
                session = session,
                capabilities = capabilities,
                generation = generation,
                frameValue = 12_044L,
                scenarioId = "inverse-winding-hole-opposite",
                draws = listOf(
                    SmokeDraw.Path(
                        stencilFan(
                            contours = rectangularRingContours(holeOppositeOrientation = true),
                            fillRule = GPUCorePrimitiveFillRule.Winding,
                            inverseFill = true,
                        ),
                        SmokeColor(240, 176, 32),
                    ),
                ),
            )
            assertPixel(inverseWinding, 32, 1, 1, 240, 176, 32, 255)
            assertPixel(inverseWinding, 32, 16, 16, 240, 176, 32, 255)
            assertPixel(inverseWinding, 32, 6, 16, 0, 0, 0, 0)

            val inverseEvenOdd = renderScenario(
                session = session,
                capabilities = capabilities,
                generation = generation,
                frameValue = 12_045L,
                scenarioId = "inverse-even-odd-hole-same",
                draws = listOf(
                    SmokeDraw.Path(
                        stencilFan(
                            contours = rectangularRingContours(holeOppositeOrientation = false),
                            fillRule = GPUCorePrimitiveFillRule.EvenOdd,
                            inverseFill = true,
                        ),
                        SmokeColor(192, 48, 208),
                    ),
                ),
            )
            assertPixel(inverseEvenOdd, 32, 1, 1, 192, 48, 208, 255)
            assertPixel(inverseEvenOdd, 32, 16, 16, 192, 48, 208, 255)
            assertPixel(inverseEvenOdd, 32, 6, 16, 0, 0, 0, 0)

            val resetGeometry = stencilFan(
                contours = listOf(
                    listOf(5f to 5f, 27f to 5f, 27f to 27f, 5f to 27f),
                ),
                fillRule = GPUCorePrimitiveFillRule.EvenOdd,
            )
            val resetChain = renderScenario(
                session = session,
                capabilities = capabilities,
                generation = generation,
                frameValue = 12_046L,
                scenarioId = "direct-path-direct-path-reset",
                draws = listOf(
                    SmokeDraw.Direct(GPURect(0f, 0f, 32f, 32f), SmokeColor(30, 40, 50)),
                    SmokeDraw.Path(resetGeometry, SmokeColor(220, 40, 40)),
                    SmokeDraw.Direct(GPURect(12f, 12f, 20f, 20f), SmokeColor(32, 220, 64)),
                    SmokeDraw.Path(resetGeometry, SmokeColor(40, 80, 240)),
                ),
            )
            assertPixel(resetChain, 32, 2, 2, 30, 40, 50, 255)
            assertPixel(resetChain, 32, 7, 7, 40, 80, 240, 255)
            assertPixel(resetChain, 32, 16, 16, 40, 80, 240, 255)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    private fun rectangularRingContours(
        holeOppositeOrientation: Boolean,
    ): List<List<Pair<Float, Float>>> {
        val outer = listOf(3f to 3f, 29f to 3f, 29f to 29f, 3f to 29f)
        val holeSameOrientation = listOf(10f to 10f, 22f to 10f, 22f to 22f, 10f to 22f)
        return listOf(
            outer,
            if (holeOppositeOrientation) holeSameOrientation.reversed() else holeSameOrientation,
        )
    }

    private fun buildAnalyticShapeSmokeFrame(
        capabilities: org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities,
        generation: GPUDeviceGenerationID,
        frameValue: Long,
        scenarioId: String,
        commandIdBase: Int,
    ): AnalyticShapeSmokeFrame {
        val frameId = GPUFrameID(frameValue)
        val readbackId = GPUReadbackRequestID("readback.core-primitive.$scenarioId")
        val targetBounds = GPUPixelBounds(0, 0, 32, 32)
        val target = GPUTargetFacts(32, 32, "rgba8unorm")
        val wideOpen = GPUClipFacts(
            kind = GPUClipKind.WideOpen,
            bounds = GPUBounds(0f, 0f, 32f, 32f),
            coveragePlan = GPUClipCoveragePlan.NoClip,
        )
        val rectAa = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(commandIdBase),
            rect = GPURect(1.25f, 2.25f, 8.75f, 10.75f),
            target = target,
            material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
            clip = wideOpen,
            paintOrder = 0,
            source = GPUCommandSource("unit-test", "fillRect", GPUFrameProvenance.GmContent),
        ).copy(antiAlias = true)
        val rrectHard = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(commandIdBase + 1),
            rrect = GPURRect(
                rect = GPURect(11f, 2f, 21f, 12f),
                topLeft = GPURRectCornerRadii(3f, 2f),
                topRight = GPURRectCornerRadii(1f, 3f),
                bottomRight = GPURRectCornerRadii(4f, 1f),
                bottomLeft = GPURRectCornerRadii(2f, 4f),
            ),
            target = target,
            material = GPUMaterialDescriptor.SolidColor(0f, 1f, 0f, 1f),
            clip = wideOpen,
            paintOrder = 1,
            source = GPUCommandSource("unit-test", "fillRRect", GPUFrameProvenance.GmContent),
        ).copy(antiAlias = false)
        val rrectAa = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(commandIdBase + 2),
            rrect = GPURRect(
                rect = GPURect(11.25f, 17.25f, 27.75f, 29.75f),
                topLeft = GPURRectCornerRadii(3f, 2f),
                topRight = GPURRectCornerRadii(2f, 4f),
                bottomRight = GPURRectCornerRadii(4f, 2f),
                bottomLeft = GPURRectCornerRadii(1f, 3f),
            ),
            target = target,
            material = GPUMaterialDescriptor.SolidColor(0f, 0f, 1f, 1f),
            clip = wideOpen,
            paintOrder = 2,
            source = GPUCommandSource("unit-test", "fillRRect", GPUFrameProvenance.GmContent),
        ).copy(antiAlias = true)
        val commands: List<NormalizedDrawCommand> = listOf(rectAa, rrectHard, rrectAa)
        val base = GPURecorder(
            GPURecordingID("recording.core.smoke.$scenarioId"),
            frameId,
            capabilities,
            generation,
        ).apply {
            commands.forEach(::record)
        }.close().taskList.withClipPlans(commands.associate { command ->
            command.commandId.value to GPUClipExecutionPlan.NoClip
        })
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        assertEquals(3, packets.size, "Analytic-shape smoke base recording refused: ${base.diagnostics}")
        val commandsById = commands.associateBy { command -> command.commandId.value }
        val semantics = packets.associate { packet ->
            val command = requireNotNull(commandsById[packet.commandIdValue])
            packet.commandIdValue to command.analyticShapeCoreSemantic(packet, targetBounds)
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
        return AnalyticShapeSmokeFrame(taskList, readbackId)
    }

    private fun renderScenario(
        session: GPUPreparedSceneFrameSession,
        capabilities: org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities,
        generation: GPUDeviceGenerationID,
        frameValue: Long,
        scenarioId: String,
        draws: List<SmokeDraw>,
    ): ByteArray {
        val frameId = GPUFrameID(frameValue)
        val readbackId = GPUReadbackRequestID("readback.core-primitive.$scenarioId")
        val targetBounds = GPUPixelBounds(0, 0, 32, 32)
        val commands = draws.mapIndexed { index, draw ->
            draw.baseCommand(
                commandId = 400 + (frameValue - 12_000L).toInt() * 10 + index,
                paintOrder = index,
            )
        }
        val base = GPURecorder(
            GPURecordingID("recording.core.smoke.$scenarioId"),
            frameId,
            capabilities,
            generation,
        ).apply {
            commands.forEach(::record)
        }.close().taskList.withClipPlans(commands.associate { command ->
            command.commandId.value to GPUClipExecutionPlan.NoClip
        })
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        assertEquals(draws.size, packets.size, "Core path smoke base recording refused: ${base.diagnostics}")
        val drawsByCommand = commands.mapIndexed { index, command -> command.commandId.value to draws[index] }.toMap()
        val commandsById = commands.associateBy { command -> command.commandId.value }
        val semantics = packets.associate { packet ->
            val draw = requireNotNull(drawsByCommand[packet.commandIdValue])
            packet.commandIdValue to draw.semantic(
                packet = packet,
                command = requireNotNull(commandsById[packet.commandIdValue]),
                targetBounds = targetBounds,
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
        val expectedDrawIndexed = draws.sumOf { draw -> if (draw is SmokeDraw.Path) 2 else 1 }
        val preparedRender = taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals(expectedDrawIndexed, preparedRender.drawPackets.size)
        val framePlan = GPUFramePlanner.plan(taskList)
        assertEquals(1, framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().size)
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
        assertEquals(1L, nativeAfter.encoders - nativeBefore.encoders)
        assertEquals(1L, nativeAfter.commandBuffers - nativeBefore.commandBuffers)
        assertEquals(1L, renderAfter.renderPasses - renderBefore.renderPasses)
        assertEquals(1L, nativeAfter.submits - nativeBefore.submits)
        assertEquals(1L, nativeAfter.readbackCopies - nativeBefore.readbackCopies)
        assertEquals(
            expectedDrawIndexed.toLong(),
            renderAfter.drawIndexed - renderBefore.drawIndexed,
        )
        return assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
    }

    private fun renderAnalyticScenario(
        session: GPUPreparedSceneFrameSession,
        capabilities: org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities,
        generation: GPUDeviceGenerationID,
        frameValue: Long,
        scenarioId: String,
        draws: List<AnalyticSmokeDraw>,
    ): ByteArray {
        val frameId = GPUFrameID(frameValue)
        val readbackId = GPUReadbackRequestID("readback.core-primitive.$scenarioId")
        val targetBounds = GPUPixelBounds(0, 0, 32, 32)
        val commands = draws.mapIndexed { index, draw ->
            GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(700 + (frameValue - 12_000L).toInt() * 10 + index),
                rect = GPURect(0f, 0f, 32f, 32f),
                target = GPUTargetFacts(32, 32, "rgba8unorm"),
                material = GPUMaterialDescriptor.SolidColor(
                    draw.color.red / 255f,
                    draw.color.green / 255f,
                    draw.color.blue / 255f,
                    draw.color.alpha / 255f,
                ),
                clip = GPUClipFacts(
                    kind = GPUClipKind.WideOpen,
                    bounds = GPUBounds(0f, 0f, 32f, 32f),
                    coveragePlan = GPUClipCoveragePlan.NoClip,
                ),
                paintOrder = index,
                source = GPUCommandSource("unit-test", "fillRect", GPUFrameProvenance.GmContent),
            ).copy(antiAlias = false)
        }
        val base = GPURecorder(
            GPURecordingID("recording.core.smoke.$scenarioId"),
            frameId,
            capabilities,
            generation,
        ).apply {
            commands.forEach(::record)
        }.close().taskList.withClipPlans(commands.mapIndexed { index, command ->
            command.commandId.value to draws[index].clip
        }.toMap())
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        assertEquals(draws.size, packets.size, "Core analytic smoke base recording refused: ${base.diagnostics}")
        val commandsById = commands.associateBy { command -> command.commandId.value }
        val semantics = packets.associate { packet ->
            val command = requireNotNull(commandsById[packet.commandIdValue])
            packet.commandIdValue to command.coreSemantic(packet, targetBounds, targetBounds)
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
        assertEquals(draws.size, preparedRender.drawPackets.size)
        assertEquals(
            1,
            preparedRender.drawPackets.map { packet ->
                requireNotNull(packet.corePrimitivePreparedAuthority).structuralPipelineKey
            }.distinct().size,
        )
        val framePlan = GPUFramePlanner.plan(taskList)
        val forbiddenAttachmentRoles = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .filter { request -> request.role in setOf(
                GPUFrameResourceRole.ClipMask,
                GPUFrameResourceRole.ClipDepthStencil,
                GPUFrameResourceRole.PathDepthStencil,
            ) }
        assertTrue(forbiddenAttachmentRoles.isEmpty())

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
        assertEquals(1L, renderAfter.renderPasses - renderBefore.renderPasses)
        assertEquals(draws.size.toLong(), renderAfter.drawIndexed - renderBefore.drawIndexed)
        return assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
    }

    private fun stencilFan(
        contours: List<List<Pair<Float, Float>>>,
        fillRule: GPUCorePrimitiveFillRule,
        inverseFill: Boolean = false,
    ): GPUCorePrimitiveGeometryInput.TriangulatedPath {
        require(contours.isNotEmpty() && contours.all { contour -> contour.size >= 3 })
        val sourcePoints = contours.flatten()
        val anchor = sourcePoints.first()
        val sourceContourStarts = buildList {
            var start = 0
            contours.forEach { contour ->
                add(start)
                start += contour.size
            }
        }
        val fanVertices = buildList {
            contours.forEach { contour ->
                contour.indices.forEach { index ->
                    val next = contour[(index + 1) % contour.size]
                    add(anchor.first)
                    add(anchor.second)
                    add(contour[index].first)
                    add(contour[index].second)
                    add(next.first)
                    add(next.second)
                }
            }
        }
        return GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = fanVertices,
            indices = List(sourcePoints.size * 3) { index -> index },
            sourceContourStarts = sourceContourStarts,
            sourceVertexCount = sourcePoints.size,
            coverBounds = sourcePoints.toPixelCoverBounds(GPUPixelBounds(0, 0, 32, 32)),
            geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
            fillRule = fillRule,
            inverseFill = inverseFill,
        )
    }

    private sealed interface SmokeDraw {
        val color: SmokeColor

        data class Direct(
            val rect: GPURect,
            override val color: SmokeColor,
        ) : SmokeDraw

        data class Path(
            val geometry: GPUCorePrimitiveGeometryInput.TriangulatedPath,
            override val color: SmokeColor,
        ) : SmokeDraw
    }

    private data class AnalyticSmokeDraw(
        val clip: GPUClipExecutionPlan,
        val color: SmokeColor,
    )

    private data class AnalyticShapeSmokeFrame(
        val taskList: GPUTaskList,
        val readbackId: GPUReadbackRequestID,
    )

    private data class SmokeColor(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int = 255,
    ) {
        init {
            require(listOf(red, green, blue, alpha).all { component -> component in 0..255 })
        }

        val normalized: List<Float>
            get() {
                val alphaValue = alpha / 255f
                return listOf(
                    red / 255f * alphaValue,
                    green / 255f * alphaValue,
                    blue / 255f * alphaValue,
                    alphaValue,
                )
            }
    }

    private fun SmokeDraw.baseCommand(
        commandId: Int,
        paintOrder: Int,
    ): org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.FillRect {
        val bounds = when (this) {
            is SmokeDraw.Direct -> rect
            is SmokeDraw.Path -> geometry.coverBounds.let { cover ->
                GPURect(
                    cover.left.toFloat(),
                    cover.top.toFloat(),
                    cover.right.toFloat(),
                    cover.bottom.toFloat(),
                )
            }
        }
        return GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(commandId),
            rect = bounds,
            target = GPUTargetFacts(32, 32, "rgba8unorm"),
            material = GPUMaterialDescriptor.SolidColor(
                color.red / 255f,
                color.green / 255f,
                color.blue / 255f,
                color.alpha / 255f,
            ),
            clip = GPUClipFacts(
                kind = GPUClipKind.WideOpen,
                bounds = GPUBounds(0f, 0f, 32f, 32f),
                coveragePlan = GPUClipCoveragePlan.NoClip,
            ),
            paintOrder = paintOrder,
            source = GPUCommandSource("unit-test", "fillRect", GPUFrameProvenance.GmContent),
        ).copy(antiAlias = false)
    }

    private fun SmokeDraw.semantic(
        packet: GPUDrawPacket,
        command: org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.FillRect,
        targetBounds: GPUPixelBounds,
    ) = when (this) {
        is SmokeDraw.Direct -> command.coreSemantic(packet, targetBounds, targetBounds)
        is SmokeDraw.Path -> GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = packet.commandIdValue,
                sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                geometry = geometry,
                premultipliedRgba = color.normalized,
                targetBounds = targetBounds,
                scissorBounds = if (geometry.inverseFill) targetBounds else geometry.coverBounds,
                clipCoveragePlan = requireNotNull(packet.clipCoveragePlan),
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = packet.frameProvenance,
                coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
            ),
        )
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

    private fun assertPartialPrimaryPixel(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        channel: Int,
    ) {
        val offset = (y * width + x) * 4
        val pixel = (0..3).map { bytes[offset + it].toInt() and 0xff }
        assertTrue(pixel[channel] in 1..254, "Expected a partial coverage pixel, observed $pixel")
        assertEquals(pixel[channel], pixel[3])
        pixel.indices.filter { it != channel && it != 3 }.forEach { assertEquals(0, pixel[it]) }
    }

    private fun assertPixelChannelIn(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        channel: Int,
        expected: IntRange,
    ) {
        val offset = (y * width + x) * 4
        val pixel = (0..3).map { bytes[offset + it].toInt() and 0xff }
        assertTrue(pixel[channel] in expected, "Expected channel $channel in $expected, observed $pixel")
        assertEquals(pixel[channel], pixel[3])
        pixel.indices.filter { it != channel && it != 3 }.forEach { assertEquals(0, pixel[it]) }
    }

    private fun org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.FillRect.coreSemantic(
        packet: GPUDrawPacket,
        targetBounds: GPUPixelBounds,
        scissorBounds: GPUPixelBounds,
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
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
            coverageMode = coverageMode,
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

    private fun NormalizedDrawCommand.analyticShapeCoreSemantic(
        packet: GPUDrawPacket,
        targetBounds: GPUPixelBounds,
    ) = when (this) {
        is NormalizedDrawCommand.FillRect -> coreSemantic(
            packet = packet,
            targetBounds = targetBounds,
            scissorBounds = targetBounds,
            coverageMode = if (antiAlias) {
                GPUCorePrimitiveCoverageMode.ScalarAA
            } else {
                GPUCorePrimitiveCoverageMode.FullOrScissor
            },
        )
        is NormalizedDrawCommand.FillRRect -> {
            val normalized = assertIs<GPURRectNormalizationResult.Accepted>(
                GPURRectNormalizer.normalize(rrect),
            )
            val authority = assertIs<GPUCorePrimitiveRRectGeometryAuthorityIssue.Issued>(
                corePrimitiveRRectGeometryAuthority(rrect, normalized, transform),
            ).authority
            GPUCorePrimitivePayloadGatherer().gatherSemantic(
                GPUCorePrimitivePayloadInput(
                    commandIdValue = commandId.value,
                    sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
                    geometry = authority.sealedDeviceGeometryInput(),
                    premultipliedRgba = (material as GPUMaterialDescriptor.SolidColor).let { color ->
                        listOf(color.r * color.a, color.g * color.a, color.b * color.a, color.a)
                    },
                    targetBounds = targetBounds,
                    scissorBounds = targetBounds,
                    clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                    blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                    frameProvenance = GPUFrameProvenance.GmContent,
                    coverageMode = if (antiAlias) {
                        GPUCorePrimitiveCoverageMode.ScalarAA
                    } else {
                        GPUCorePrimitiveCoverageMode.FullOrScissor
                    },
                    analysisRecordId = "analysis.fill_rrect.${commandId.value}",
                    analysisCommandFamily = "FillRRect",
                    rrectGeometryAuthority = authority,
                ),
            )
        }
        else -> error("Analytic-shape smoke accepts only FillRect and FillRRect commands")
    }

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
