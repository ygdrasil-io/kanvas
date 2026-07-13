package org.graphiks.kanvas.surface.gpu

import kotlin.math.pow
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
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.graphiks.kanvas.types.withAlphaByte
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `all destination-read blends retain clipped source and use no readback`() {
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

        expectedByMode.forEach { mode ->
            val result = renderClippedBlend(destination, source, mode)

            assertPixelNear(result.pixels, 4, 4, destination, tolerance = 0)
            assertPixelNear(result.pixels, 20, 20, expectedBlend(source, destination, mode), tolerance = 2)
            assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        }
        val partialAlpha = renderClippedBlend(
            destination,
            Color.RED.withAlphaByte(128),
            BlendMode.DARKEN,
        )
        val after = GPUBackendRuntimeFactory.createOrNull()!!.runtimeTelemetry

        assertPixelNear(partialAlpha.pixels, 4, 4, destination, tolerance = 0)
        assertTrue(alphaAt(partialAlpha.pixels, 20, 20) > 0)
        assertEquals(0, partialAlpha.diagnostics.fatalCount, partialAlpha.diagnostics.entries.toString())
        assertEquals(before.destinationReadbackSnapshots, after.destinationReadbackSnapshots)
        assertTrue(after.destinationCopies - before.destinationCopies >= 7L)
    }

    @Test
    fun `scissor destination read blend preserves destination outside the clip`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val result = Surface(width = 32, height = 32).run {
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

        assertPixelNear(result.pixels, 4, 4, Color.WHITE, tolerance = 0)
        assertPixelNear(result.pixels, 16, 16, Color.BLACK, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
    }

    @Test
    fun destinationReadMaskBlurUsesIndependentGeometryCoverage() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val result = Surface(width = 32, height = 32).run {
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

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { it.reason == "gpu-copy-then-formula" },
            result.diagnostics.entries.toString(),
        )
        assertPixelNear(result.pixels, 16, 16, Color.BLACK, tolerance = 3)
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

        val result = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                save()
                clipRect(Rect(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = true)
                drawPicture(picture)
                restore()
            }
            render()
        }

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { it.reason == "gpu-copy-then-formula" },
            result.diagnostics.entries.toString(),
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

        val result = Surface(width = 32, height = 32).run {
            canvas {
                drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
                drawPicture(parentPicture)
            }
            render()
        }

        // Black COLOR_DODGE over white is white. The clipped blue child makes the captured
        // child clip observable without changing that blend expectation.
        assertPixelNear(result.pixels, 4, 4, Color.WHITE, tolerance = 0)
        assertPixelNear(result.pixels, 16, 16, Color.BLUE, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { it.reason == "gpu-copy-then-formula" },
            result.diagnostics.entries.toString(),
        )
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

    private fun expectedBlend(source: Color, destination: Color, mode: BlendMode): Color {
        val src = floatArrayOf(source.redByte.toFloat(), source.greenByte.toFloat(), source.blueByte.toFloat())
            .map { srgbToLinear(it / 255f) }
        val dst = floatArrayOf(destination.redByte.toFloat(), destination.greenByte.toFloat(), destination.blueByte.toFloat())
            .map { srgbToLinear(it / 255f) }
        val blended = List(3) { channel ->
            val s = src[channel]
            val d = dst[channel]
            when (mode) {
                BlendMode.MULTIPLY -> s * d
                BlendMode.SCREEN -> s + d - s * d
                BlendMode.OVERLAY -> if (d <= 0.5f) 2f * s * d else 1f - 2f * (1f - s) * (1f - d)
                BlendMode.DARKEN -> minOf(s, d)
                BlendMode.LIGHTEN -> maxOf(s, d)
                BlendMode.DIFFERENCE -> kotlin.math.abs(d - s)
                BlendMode.EXCLUSION -> s + d - 2f * s * d
                else -> error("Not a destination-read mode: $mode")
            }
        }
        return Color.fromRGBA(
            linearToSrgb(blended[0]),
            linearToSrgb(blended[1]),
            linearToSrgb(blended[2]),
        )
    }

    private fun srgbToLinear(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(value: Float): Float =
        if (value <= 0.0031308f) value * 12.92f else 1.055f * value.pow(1f / 2.4f) - 0.055f

    private fun assertPixelNear(pixels: UByteArray, x: Int, y: Int, color: Color, tolerance: Int) {
        val offset = (y * 32 + x) * 4
        val actual = listOf(
            pixels[offset].toInt(),
            pixels[offset + 1].toInt(),
            pixels[offset + 2].toInt(),
            pixels[offset + 3].toInt(),
        )
        val expected = listOf(color.redByte, color.greenByte, color.blueByte, color.alphaByte)
        assertTrue(kotlin.math.abs(expected[0] - actual[0]) <= tolerance, "red at ($x,$y) actual=$actual expected=$expected")
        assertTrue(kotlin.math.abs(expected[1] - actual[1]) <= tolerance, "green at ($x,$y) actual=$actual expected=$expected")
        assertTrue(kotlin.math.abs(expected[2] - actual[2]) <= tolerance, "blue at ($x,$y) actual=$actual expected=$expected")
        assertTrue(kotlin.math.abs(expected[3] - actual[3]) <= tolerance, "alpha at ($x,$y) actual=$actual expected=$expected")
    }

    private fun alphaAt(pixels: UByteArray, x: Int, y: Int): Int =
        pixels[(y * 32 + x) * 4 + 3].toInt()
}
