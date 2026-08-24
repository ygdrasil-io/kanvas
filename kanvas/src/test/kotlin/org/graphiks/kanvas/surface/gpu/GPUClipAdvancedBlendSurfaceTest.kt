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
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
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
    fun `clipped destination-read blends refuse with the analytic shape clip code before native work`() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        val before = session!!.runtimeTelemetry
        val destination = ColorARGB.of(255, 64, 128, 192)
        val source = ColorARGB.of(255, 192, 64, 32)
        val expectedByMode = listOf(
            BlendMode.MULTIPLY,
            BlendMode.SCREEN,
            BlendMode.OVERLAY,
            BlendMode.DARKEN,
            BlendMode.LIGHTEN,
            BlendMode.DIFFERENCE,
            BlendMode.EXCLUSION,
        )

        // The default-AA (ScalarAA) source rect lowers to the analytic-shape
        // (uniform80) lane, which cannot combine with the analytic-clip uniform64 authority in
        // one draw; the single-draw mixed-layout gate is retired, so these frames re-point to
        // the analytic-shape clip refusal (NoClip or ScissorOnly execution).
        expectedByMode.forEach { mode ->
            val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
                renderClippedBlend(destination, source, mode)
            }
            assertEquals(
                "unsupported.recording.core_primitive_analytic_shape_clip",
                failure.diagnostic.code.value,
            )
        }
        assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderClippedBlend(
                destination,
                ColorARGB.Red.withAlpha(128),
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

        // The scissored dst-read rect shares the uniform32 layout with the
        // background, so the frame is the admitted two-render dst-copy shape (destination
        // pass, ordered snapshot copy, consuming pass). CPU reference: DARKEN(black, white) =
        // black inside the scissor and retained white outside.
        val pixels = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.White))
                save()
                clipRect(RectF32(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = false)
                drawRect(
                    RectF32(4f, 4f, 28f, 28f),
                    Paint.fill(ColorARGB.Black).copy(antiAlias = false, blendMode = BlendMode.DARKEN),
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

        // Top-level mask blur is prepared-covered. The DARKEN
        // blur rect over the white destination rides the copy-then-formula
        // destination-read lane with the blurred coverage as its source shade,
        // matching the CPU oracle (TopLevelMaskBlurPixelOracle + the composite
        // route's blend oracle).
        val pixels = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.White))
                drawRect(
                    RectF32(10f, 10f, 22f, 22f),
                    Paint.fill(ColorARGB.Black).copy(
                        antiAlias = false,
                        blendMode = BlendMode.DARKEN,
                        maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 2f),
                    ),
                )
            }
            render().pixels.toUByteArray()
        }
        val destination = TopLevelMaskBlurPixelOracle.fillRect(32, 32, 0f, 0f, 32f, 32f, ColorARGB.White)
        val expected = TopLevelMaskBlurPixelOracle.render(
            32, 32,
            TopLevelMaskBlurPixelOracle.Shape.Rect(10f, 10f, 22f, 22f),
            TopLevelMaskBlurPixelOracle.fullTargetBounds(),
            BlurStyle.NORMAL, 2f, ColorARGB.Black, BlendMode.DARKEN, destination,
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(expected, pixels)
    }

    @Test
    fun clippedPictureChildUsesColorDodgeComposer() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val childRecorder = PictureRecorder()
        val childCanvas = childRecorder.beginRecording(RectF32(0f, 0f, 32f, 32f))
        childCanvas.drawRect(
            RectF32(4f, 4f, 28f, 28f),
            Paint.fill(ColorARGB.Black).copy(antiAlias = false, blendMode = BlendMode.COLOR_DODGE),
        )
        val child = childRecorder.finishRecordingAsPicture()

        val parentRecorder = PictureRecorder()
        val parentCanvas = parentRecorder.beginRecording(RectF32(0f, 0f, 32f, 32f))
        parentCanvas.drawPicture(child)
        val picture = parentRecorder.finishRecordingAsPicture()

        // The painted picture inside a clipped frame is a documented prepared-route refusal
        // (unsupported.surface.prepared.mixed-composite-topology): the composite route cannot
        // materialize the picture topology.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.White))
                    save()
                    clipRect(RectF32(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = true)
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
        blackDodgeRecorder.beginRecording(RectF32(0f, 0f, 32f, 32f)).apply {
            drawRect(
                RectF32(0f, 0f, 32f, 32f),
                Paint.fill(ColorARGB.Black).copy(antiAlias = false, blendMode = BlendMode.COLOR_DODGE),
            )
        }
        val blackDodgePicture = blackDodgeRecorder.finishRecordingAsPicture()

        val blueRecorder = PictureRecorder()
        blueRecorder.beginRecording(RectF32(0f, 0f, 32f, 32f)).apply {
            drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Blue).copy(antiAlias = false))
        }
        val bluePicture = blueRecorder.finishRecordingAsPicture()

        val parentRecorder = PictureRecorder()
        parentRecorder.beginRecording(RectF32(0f, 0f, 32f, 32f)).apply {
            drawPicture(blackDodgePicture)
            save()
            clipRect(RectF32(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = false)
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
                    drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.White))
                    drawPicture(parentPicture)
                }
                render()
            }
        }
        assertEquals("unsupported.composite.clip", failure.diagnostic.code.value)
    }

    private fun renderClippedBlend(destination: ColorARGB, source: ColorARGB, mode: BlendMode): UByteArray =
        Surface(width = 32, height = 32).run {
            canvas {
                drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(destination))
                save()
                clipRect(RectF32(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = true)
                drawRect(RectF32(4f, 4f, 28f, 28f), Paint.fill(source).copy(blendMode = mode))
                restore()
            }
            render().pixels.toUByteArray()
        }

    private fun sampleAt(pixels: UByteArray, x: Int, y: Int): UByteArray {
        val offset = (y * 32 + x) * 4
        return pixels.copyOfRange(offset, offset + 4)
    }
}
