package org.graphiks.kanvas.surface.gpu

import kotlin.test.assertFailsWith
import org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTerminalException
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.withAlphaByte
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUClipAdvancedBlendSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `clipped destination-read blends refuse with the mixed uniform layouts code before native work`() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        val before = session!!.runtimeTelemetry
        val destination = Color.fromArgb(255, 64, 128, 192)
        val source = Color.fromArgb(255, 192, 64, 32)
        val expectedByMode = listOf(
            BlendMode.MULTIPLY,
            BlendMode.SCREEN,
            BlendMode.OVERLAY,
            BlendMode.DARKEN,
            BlendMode.LIGHTEN,
            BlendMode.DIFFERENCE,
            BlendMode.EXCLUSION,
        )

        // FP-09 terminal refusal: an analytic (AA) clip over an analytic-shape dst-read
        // draw mixes the analytic-shape uniform80 lane with the analytic-clip uniform64/
        // uniform160 lane — the designed mixed-uniform-layouts family. Pre-FP-09 these
        // frames rendered via the legacy renderer (green at the FP-08 tip accaea616); the
        // route collapse converted them to this stable code (Task 6 evidence family 4).
        expectedByMode.forEach { mode ->
            val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
                renderClippedBlend(destination, source, mode)
            }
            assertEquals(
                "unsupported.recording.core_primitive_mixed_uniform_layouts",
                failure.diagnostic.code.value,
            )
        }
        assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderClippedBlend(
                destination,
                Color.RED.withAlphaByte(128),
                BlendMode.DARKEN,
            )
        }
        val after = GPUBackendRuntimeFactory.createOrNull()!!.runtimeTelemetry

        // The terminal refusal allocates no destination snapshot before failing.
        assertEquals(before.destinationReadbackSnapshots, after.destinationReadbackSnapshots)
        assertEquals(before.destinationCopies, after.destinationCopies)
    }

    @Test
    fun `scissor destination read blend refuses with the mixed uniform layouts code before encoding`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        // FP-09 terminal refusal: the frame mixes an unclipped uniform32 rect with a
        // scissored dst-read rect (uniform64 lane); pre-FP-09 the legacy renderer
        // rendered it. See the sibling analytic-clip case for the family reference.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(Rect(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = false)
                    drawRect(
                        Rect(4f, 4f, 28f, 28f),
                        Paint.fill(Color.BLACK).copy(antiAlias = false, blendMode = BlendMode.DARKEN),
                    )
                    restore()
                }
                render()
            }
        }
        assertEquals(
            "unsupported.recording.core_primitive_mixed_uniform_layouts",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun destinationReadMaskBlurRefusesWithAnalysisAuthorityMissing() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        // FP-09 terminal refusal: a top-level mask-blur rect frame carries no rect route
        // authority in its analysis record — the designed analysis_authority_missing code
        // (Task 6 evidence §4, mask-blur rect frames). Pre-FP-09 the legacy renderer
        // materialized the blur mask; the legacy mask machinery is deleted (Task 8) and
        // the prepared top-level blur route is not wired, so the frame refuses loudly.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    drawRect(
                        Rect(10f, 10f, 22f, 22f),
                        Paint.fill(Color.BLACK).copy(
                            antiAlias = false,
                            blendMode = BlendMode.DARKEN,
                            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 2f),
                        ),
                    )
                }
                render()
            }
        }
        assertEquals(
            "unsupported.core_primitive.rect.analysis_authority_missing",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun clippedPictureChildUsesColorDodgeComposer() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val childRecorder = PictureRecorder()
        val childCanvas = childRecorder.beginRecording(Rect(0f, 0f, 32f, 32f))
        childCanvas.drawRect(
            Rect(4f, 4f, 28f, 28f),
            Paint.fill(Color.BLACK).copy(antiAlias = false, blendMode = BlendMode.COLOR_DODGE),
        )
        val child = childRecorder.finishRecordingAsPicture()

        val parentRecorder = PictureRecorder()
        val parentCanvas = parentRecorder.beginRecording(Rect(0f, 0f, 32f, 32f))
        parentCanvas.drawPicture(child)
        val picture = parentRecorder.finishRecordingAsPicture()

        // The painted picture inside a clipped frame is a documented prepared-route refusal
        // (unsupported.surface.prepared.mixed-composite-topology): the composite route cannot
        // materialize the picture topology.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(Rect(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = true)
                    drawPicture(picture)
                    restore()
                }
                render()
            }
        }
        assertEquals(
            "unsupported.surface.prepared.mixed-composite-topology",
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `Picture children keep their own color dodge composer and captured clip`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val blackDodgeRecorder = PictureRecorder()
        blackDodgeRecorder.beginRecording(Rect(0f, 0f, 32f, 32f)).apply {
            drawRect(
                Rect(0f, 0f, 32f, 32f),
                Paint.fill(Color.BLACK).copy(antiAlias = false, blendMode = BlendMode.COLOR_DODGE),
            )
        }
        val blackDodgePicture = blackDodgeRecorder.finishRecordingAsPicture()

        val blueRecorder = PictureRecorder()
        blueRecorder.beginRecording(Rect(0f, 0f, 32f, 32f)).apply {
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.BLUE).copy(antiAlias = false))
        }
        val bluePicture = blueRecorder.finishRecordingAsPicture()

        val parentRecorder = PictureRecorder()
        parentRecorder.beginRecording(Rect(0f, 0f, 32f, 32f)).apply {
            drawPicture(blackDodgePicture)
            save()
            clipRect(Rect(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = false)
            drawPicture(bluePicture)
            restore()
        }
        val parentPicture = parentRecorder.finishRecordingAsPicture()

        // The picture with a captured layer clip is a documented prepared-route refusal
        // (unsupported.composite.clip): the composite capture refuses clip snapshots inside
        // layer scopes.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                    drawPicture(parentPicture)
                }
                render()
            }
        }
        assertEquals("unsupported.composite.clip", failure.diagnostic.code.value)
    }

    private fun renderClippedBlend(destination: Color, source: Color, mode: BlendMode) =
        Surface(width = 32, height = 32).run {
            canvas {
                drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(destination))
                save()
                clipRect(Rect(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = true)
                drawRect(Rect(4f, 4f, 28f, 28f), Paint.fill(source).copy(blendMode = mode))
                restore()
            }
            render()
        }
}
