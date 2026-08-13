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
import org.junit.jupiter.api.Assertions.assertTrue
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

        // FP-11 Task 6 residual (Task 8 B-row): the analytic-clip pass split stays pinned on
        // the mixed-layout refusal pending the per-step continuation design (the materializer
        // cleanup gap behind the deterministic session-close residual is fixed; the 64/160
        // split itself is not wired). Pre-FP-09 these frames rendered via the legacy renderer
        // (green at the FP-08 tip accaea616); the route collapse converted them to this
        // stable code (Task 6 evidence family 4).
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
    fun `scissor destination read blend renders prepared via the copy then formula lane`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        // FP-11 Task 6: the scissored dst-read rect shares the uniform32 layout with the
        // background, so the frame is the admitted two-render dst-copy shape (destination
        // pass, ordered snapshot copy, consuming pass) instead of the FP-09
        // mixed-uniform-layouts refusal. CPU reference: DARKEN(black, white) = black inside
        // the scissor and retained white outside.
        val pixels = Surface(width = 32, height = 32).run {
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
            render().pixels.toUByteArray()
        }
        assertEquals(0, sampleAt(pixels, 16, 16)[0].toInt(), "in-scissor pixel is DARKEN(black, white) = black")
        assertEquals(255, sampleAt(pixels, 16, 16)[3].toInt(), "in-scissor pixel is opaque")
        assertEquals(255, sampleAt(pixels, 2, 2)[0].toInt(), "outside the scissor the white destination is retained")
    }

    @Test
    fun `destination read mask blur renders prepared via the copy-then-formula lane`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        // FP-09 Task 11: top-level mask blur is prepared-covered. The DARKEN
        // blur rect over the white destination rides the copy-then-formula
        // destination-read lane with the blurred coverage as its source shade,
        // matching the CPU oracle (TopLevelMaskBlurPixelOracle + the composite
        // route's blend oracle). Pre-FP-09 the legacy renderer materialized the
        // blur mask the same way (green at the FP-08 tip accaea616).
        val pixels = Surface(width = 32, height = 32).run {
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
            render().pixels.toUByteArray()
        }
        val destination = TopLevelMaskBlurPixelOracle.fillRect(32, 32, 0f, 0f, 32f, 32f, Color.WHITE)
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32,
            TopLevelMaskBlurPixelOracle.Shape.Rect(10f, 10f, 22f, 22f),
            TopLevelMaskBlurPixelOracle.fullTargetBounds(),
            BlurStyle.NORMAL, 2f, Color.BLACK, BlendMode.DARKEN, destination,
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
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

    private fun renderClippedBlend(destination: Color, source: Color, mode: BlendMode): UByteArray =
        Surface(width = 32, height = 32).run {
            canvas {
                drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(destination))
                save()
                clipRect(Rect(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = true)
                drawRect(Rect(4f, 4f, 28f, 28f), Paint.fill(source).copy(blendMode = mode))
                restore()
            }
            render().pixels.toUByteArray()
        }

    private fun sampleAt(pixels: UByteArray, x: Int, y: Int): UByteArray {
        val offset = (y * 32 + x) * 4
        return pixels.copyOfRange(offset, offset + 4)
    }
}
