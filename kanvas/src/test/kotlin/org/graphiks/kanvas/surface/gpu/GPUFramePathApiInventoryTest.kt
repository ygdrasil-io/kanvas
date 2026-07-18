package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUFramePathApiInventoryTest {
    @Test
    fun `mixed core frame reaches one native coordinator encoder command buffer submit and readback`() {
        val surface = Surface(48, 40)
        surface.canvas {
            drawColor(Color.fromRGBA(0.05f, 0.06f, 0.07f, 1f))
            translate(1f, 2f)
            clipRect(Rect.fromLTRB(0f, 0f, 46f, 38f), ClipOp.INTERSECT, antiAlias = false)
            drawPoint(2f, 3f, Paint.fill(Color.WHITE).copy(antiAlias = false))
            drawPoints(
                PointMode.LINES,
                listOf(Point(3f, 4f), Point(14f, 9f)),
                Paint.stroke(Color.RED, 2f),
            )
            drawRect(Rect.fromLTRB(4f, 11f, 14f, 20f), Paint.fill(Color.GREEN))
            clipRRect(
                RRect(Rect.fromLTRB(1f, 1f, 43f, 35f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRRect(RRect(Rect.fromLTRB(16f, 11f, 28f, 21f), radius = 2f), Paint.fill(Color.BLUE))
            clipPath(
                Path().apply { addRect(Rect.fromLTRB(2f, 2f, 42f, 34f)) },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawDRRect(
                RRect(Rect.fromLTRB(29f, 10f, 43f, 25f), radius = 3f),
                RRect(Rect.fromLTRB(33f, 14f, 39f, 21f), radius = 1f),
                Paint.fill(Color.WHITE),
            )
            clipRect(Rect.fromLTRB(20f, 16f, 24f, 20f), ClipOp.DIFFERENCE, antiAlias = true)
            drawPath(
                Path().apply {
                    moveTo(5f, 27f)
                    lineTo(22f, 27f)
                    lineTo(13f, 36f)
                    close()
                },
                Paint.fill(Color.RED),
            )
            flushAndSnapshot(Rect.fromLTRB(0f, 0f, 48f, 40f))
        }

        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val generation = GPUDeviceGenerationID(capabilities.snapshotId.substringAfterLast('-').toLong())
        val frame = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(48, 40),
            RenderConfig.DEFAULT,
            capabilities,
            generation,
        )
        val readbackId = GPUReadbackRequestID("readback.kanvas.slice-12a.core")
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            frame,
            capabilities,
            GPUPixelBounds(0, 0, 48, 40),
            readbackId,
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        )
        val session = backend.prepareSceneFrameSession(GPUOffscreenTargetRequest(48, 40, "rgba8unorm"))
        try {
            val terminal = session.renderFrame(
                prepared.taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
            )
            assertEquals(
                frame.visualCommands.map { "packet.${it.normalized.commandId.value}.0" },
                frame.framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                    .flatMap { step -> step.drawPackets.map { it.packetId.value } },
            )
            val counters = session.nativeCounters()
            assertEquals(1L, counters.frameCoordinatorCreations)
            assertEquals(1L, counters.encoders)
            assertEquals(1L, counters.commandBuffers)
            assertEquals(1L, counters.submits)
            assertEquals(1L, counters.readbackCopies)
            assertEquals(0L, counters.destinationSnapshotCreations)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `reserved provenance partitions three draws through commands tasks steps and telemetry`() {
        val surface = Surface(32, 32)
        surface.canvas {
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "harness-background")
            drawRect(Rect.fromLTRB(1f, 2f, 5f, 7f), Paint.fill(Color.RED))
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "gm-content")
            drawRRect(RRect(Rect.fromLTRB(8f, 3f, 14f, 11f), radius = 2f), Paint.fill(Color.GREEN))
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "none")
            drawPath(
                Path().apply {
                    moveTo(18f, 2f)
                    lineTo(29f, 2f)
                    lineTo(24f, 14f)
                    close()
                },
                Paint.fill(Color.BLUE),
            )
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(), RenderConfig.DEFAULT)

        assertEquals(
            listOf(
                GPUFrameProvenance.HarnessBackground,
                GPUFrameProvenance.GmContent,
                GPUFrameProvenance.None,
            ),
            plan.visualCommands.map { it.provenance },
        )
        assertEquals(3, plan.visualCommands.size)
        assertEquals(3, plan.normalizedCommands.size)
        assertEquals(3, plan.recording.recordedCommands.size)
        assertEquals(3, plan.telemetryInputs.size)
        assertEquals(
            plan.visualCommands.map { it.provenance },
            plan.telemetryInputs.map { it.provenance },
        )

        val taskProvenance = plan.recording.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap { task -> task.frameProvenanceByPacketId.values }
        assertEquals(
            plan.visualCommands.map { it.provenance },
            taskProvenance,
            plan.recording.routeDiagnostics.joinToString("\n"),
        )

        val stepProvenance = plan.framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { step -> step.frameProvenanceByPacketId.values }
        assertEquals(plan.visualCommands.map { it.provenance }, stepProvenance)

        assertEquals(3, plan.framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .sumOf { it.drawPackets.size })
        assertEquals(0, plan.legacyDump.invocationCount)
    }

    @Test
    fun `unknown provenance annotation is inert and cannot activate a reserved value`() {
        val surface = Surface(16, 16)
        surface.canvas {
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "gm-content")
            drawRect(Rect.fromLTRB(1f, 1f, 4f, 4f), Paint.fill(Color.RED))
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "GM-CONTENT")
            drawRect(Rect.fromLTRB(5f, 1f, 8f, 4f), Paint.fill(Color.GREEN))
            drawAnnotation(Rect.EMPTY, "unrelated.annotation", "harness-background")
            drawRect(Rect.fromLTRB(9f, 1f, 12f, 4f), Paint.fill(Color.BLUE))
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(16, 16), RenderConfig.DEFAULT)

        assertEquals(
            listOf(
                GPUFrameProvenance.GmContent,
                GPUFrameProvenance.GmContent,
                GPUFrameProvenance.GmContent,
            ),
            plan.visualCommands.map { it.provenance },
        )
        assertEquals(3, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Annotation })
        assertEquals(3, plan.visualCommands.size)
    }

    @Test
    fun `core inventory captures target bounds geometry clip blend and state only markers`() {
        val surface = Surface(40, 30)
        surface.canvas {
            translate(2f, 3f)
            clipRect(Rect.fromLTRB(0f, 0f, 32f, 24f), ClipOp.INTERSECT, antiAlias = false)
            drawColor(Color.RED, BlendMode.SRC_OVER)
            clear(Color.TRANSPARENT)
            drawPoint(1f, 1f, Paint.fill(Color.GREEN).copy(antiAlias = false))
            drawPoints(
                PointMode.LINES,
                listOf(Point(2f, 2f), Point(8f, 8f)),
                Paint.stroke(Color.BLUE, 2f),
            )
            drawRect(Rect.fromLTRB(3f, 4f, 10f, 12f), Paint.fill(Color.RED))
            drawRRect(RRect(Rect.fromLTRB(11f, 4f, 18f, 12f), radius = 2f), Paint.fill(Color.GREEN))
            drawDRRect(
                RRect(Rect.fromLTRB(19f, 3f, 30f, 15f), radius = 2f),
                RRect(Rect.fromLTRB(22f, 6f, 27f, 12f), radius = 1f),
                Paint.fill(Color.BLUE),
            )
            drawPath(
                Path().apply {
                    moveTo(2f, 16f)
                    lineTo(12f, 16f)
                    lineTo(7f, 25f)
                    close()
                },
                Paint.fill(Color.WHITE),
            )
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "none")
            flushAndSnapshot(Rect.fromLTRB(0f, 0f, 40f, 30f))
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(40, 30), RenderConfig.DEFAULT)

        assertEquals(8, plan.visualCommands.size)
        plan.visualCommands.forEach { visual ->
            assertTrue(visual.targetSpaceBounds.left >= 0f)
            assertTrue(visual.targetSpaceBounds.top >= 0f)
            assertTrue(visual.targetSpaceBounds.right <= 40f)
            assertTrue(visual.targetSpaceBounds.bottom <= 30f)
            assertNotNull(visual.geometryCoverage)
            assertNotNull(visual.clipCoverage)
            assertEquals(visual.normalized.blend.mode, visual.blendPlan.mode)
            assertEquals(visual.provenance, visual.normalized.source.frameProvenance)
        }
        assertTrue(plan.visualCommands.any { it.geometryCoverage == GPUCoverageConsumption.StencilCoverage1x })
        assertTrue(plan.visualCommands.filterNot { it.normalized.source.operation == "clear" }
            .all { it.clipCoverage is GPUClipCoveragePlan.Scissor })
        assertEquals(1, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Transform })
        assertEquals(1, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Clip })
        assertEquals(1, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Annotation })
        assertEquals(1, plan.stateEvents.count { it.kind == GPUFramePathStateKind.FlushSnapshot })
        assertEquals(8, plan.normalizedCommands.size)
    }

    @Test
    fun `complex clip exposes analytic mask and stencil element execution without marker packets`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRect(Rect.fromLTRB(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true)
            clipRRect(
                RRect(
                    rect = Rect.fromLTRB(4f, 4f, 28f, 28f),
                    topLeft = CornerRadii(2f, 2f),
                    topRight = CornerRadii(2f, 2f),
                    bottomRight = CornerRadii(2f, 2f),
                    bottomLeft = CornerRadii(2f, 2f),
                ),
                ClipOp.DIFFERENCE,
                antiAlias = true,
            )
            clipPath(
                Path().apply { addRect(Rect.fromLTRB(10f, 10f, 20f, 20f)) },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(Rect.fromLTRB(0f, 0f, 32f, 32f), Paint.fill(Color.RED))
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(), RenderConfig.DEFAULT)
        val visual = plan.visualCommands.single()
        val clip = assertIs<GPUClipCoveragePlan.Mask>(visual.clipCoverage)

        assertEquals(
            setOf(
                GPUFrameClipExecution.Analytic,
                GPUFrameClipExecution.Mask,
                GPUFrameClipExecution.Stencil,
            ),
            visual.clipExecution.toSet(),
        )
        assertEquals(
            setOf(
                GPUClipCoverageElementKind.Rect,
                GPUClipCoverageElementKind.RRect,
                GPUClipCoverageElementKind.Path,
            ),
            clip.elements.map { it.kind }.toSet(),
        )
        assertEquals(3, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Clip })
        assertEquals(1, plan.framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .sumOf { it.drawPackets.size })
    }

    @Test
    fun `all 29 blend identities use the canonical shared plan on every core family`() {
        val families = listOf<(BlendMode) -> DisplayOp>(
            { mode -> DisplayOp.DrawColor(Color.RED, mode, Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPoint(2f, 2f, paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPoints(PointMode.LINES, listOf(Point(1f, 1f), Point(5f, 5f)), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawRect(Rect.fromLTRB(1f, 1f, 7f, 7f), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawRRect(RRect(Rect.fromLTRB(1f, 1f, 7f, 7f), radius = 1f), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawDRRect(RRect(Rect.fromLTRB(1f, 1f, 8f, 8f), 1f), RRect(Rect.fromLTRB(3f, 3f, 6f, 6f), 1f), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPath(triangle(), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
        )

        assertEquals(29, BlendMode.entries.size)
        families.forEach { family ->
            BlendMode.entries.forEach { mode ->
                val visual = GPUFramePathApiInventory.plan(
                    listOf(family(mode)),
                    target(16, 16),
                    RenderConfig.DEFAULT,
                ).visualCommands.single()
                assertEquals(mode.toGpuBlendFacts().mode, visual.blendPlan.mode)
                assertFalse(visual.blendPlan is GPUBlendPlan.UnsupportedBlend)
            }
        }
    }

    @Test
    fun `slice 12A families are absent from the closed legacy allowlist`() {
        assertEquals(
            setOf(
                LegacyDisplayOpFamily.Images,
                LegacyDisplayOpFamily.Text,
                LegacyDisplayOpFamily.Vertices,
                LegacyDisplayOpFamily.Composites,
            ),
            GPULegacyImmediatePathAdapter.allowedFamilies,
        )

        val adapter = GPULegacyImmediatePathAdapter()
        assertFalse(adapter.accepts(DisplayOp.DrawRect(
            Rect.fromLTRB(0f, 0f, 1f, 1f),
            Paint.fill(Color.RED),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )))
        assertTrue(adapter.accepts(legacyImageOp()))
        adapter.recordInvocation(legacyImageOp())
        assertEquals(1, adapter.dump().invocationCount)
        assertEquals(mapOf(LegacyDisplayOpFamily.Images to 1), adapter.dump().invocationsByFamily)
    }

    private fun target(width: Int = 32, height: Int = 32) =
        org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(width, height, "rgba8unorm")

    private fun paint(mode: BlendMode) = Paint.fill(Color.RED).copy(blendMode = mode)

    private fun triangle() = Path().apply {
        moveTo(1f, 1f)
        lineTo(8f, 1f)
        lineTo(4f, 8f)
        close()
    }

    private fun legacyImageOp(): DisplayOp.DrawImage {
        val image = org.graphiks.kanvas.image.Image.fromPixels(
            1,
            1,
            byteArrayOf(0, 0, 0, 0),
            sourceId = "legacy-boundary",
        )
        return DisplayOp.DrawImage(
            image,
            Rect.fromLTRB(0f, 0f, 1f, 1f),
            Rect.fromLTRB(0f, 0f, 1f, 1f),
            null,
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )
    }
}
