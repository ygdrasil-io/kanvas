@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.SceneRecordingLimitException
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals

class W5cGradientSurfacePixelTest {
    @Test
    fun linearRectNormalizesPositionsAndHardStopRuns() {
        val red = ubyteArrayOf(255u, 0u, 0u, 255u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
        val fixtures = listOf(
            // Clamp both ends, then monotonize .25 into the hard stop at .5.
            listOf(GradientStop(-1f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
                GradientStop(.25f, ColorARGB.Blue), GradientStop(2f, ColorARGB.Blue)) to listOf(red, red, blue, blue, blue),
            // Both implicit endpoints must repeat the nearest input color.
            listOf(GradientStop(.25f, ColorARGB.Red), GradientStop(.25f, ColorARGB.Blue),
                GradientStop(.75f, ColorARGB.Blue), GradientStop(.75f, ColorARGB.Red)) to listOf(red, blue, blue, red, red),
            // Interior colors in a run longer than two never replace its first or last.
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
                GradientStop(.5f, ColorARGB.Green), GradientStop(.5f, ColorARGB.Black),
                GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue)) to listOf(red, red, blue, blue, blue),
        )
        for ((stops, expected) in fixtures) {
            val bounds = RectF32.ofLTRB(0f, 0f, 5f, 1f)
            val recorder = PictureRecorder()
            recorder.beginRecording(bounds).drawRect(bounds, Paint(shader = Shader.LinearGradient(
                Point2F32(.5f, 0f), Point2F32(4.5f, 0f), stops), antiAlias = false))
            val picture = recorder.finishRecordingAsPicture()
            val surface = Surface(5, 1)
            surface.canvas { picture.playback(this) }
            val pixels = surface.render().pixels
            expected.forEachIndexed { indexI32, color ->
                assertContentEquals(color, pixels.copyOfRange(indexI32 * 4, indexI32 * 4 + 4))
            }
        }
    }

    @Test
    fun linearRectDegenerateAxisUsesLastStop() {
        for (endF32 in listOf(Point2F32(0f, 0f), Point2F32(0.0000152587890625f, 0f))) {
            val surface = Surface(1, 1)
            surface.canvas { drawRect(rect, Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), endF32,
                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Green),
                    GradientStop(1f, ColorARGB.Blue))), antiAlias = false)) }
            assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), surface.render().pixels)
        }
    }

    @Test
    fun linearRectInvalidInputsRefusePubliclyAndAllowRecovery() {
        for (shader in listOf(linearStops(2).copy(start = Point2F32(Float.NaN, 0f)),
            linearStops(2).copy(stops = listOf(GradientStop(Float.POSITIVE_INFINITY, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))))) {
            val surface = Surface(1, 1)
            surface.canvas { drawRect(rect, Paint(shader = shader, antiAlias = false)) }
            val captured = assertInstanceOf(SceneCaptureResult.Invalid::class.java, surface.snapshotScene())
            assertEquals("non-finite-value", captured.diagnostics.single().code.value)
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals("non-finite-value", failure.message.orEmpty().substringBefore(':'))
        }
        // Finite public inputs whose per-fragment numeric domain cannot be proven must never execute.
        for (shader in listOf(linearStops(2).copy(end = Point2F32(1e20f, 0f)),
            linearStops(2).copy(start = Point2F32(1_000_000f, 0f), end = Point2F32(1_000_001f, 0f)))) {
            val surface = Surface(1, 1)
            surface.canvas { drawRect(rect, Paint(shader = shader, antiAlias = false)) }
            assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene())
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals("unsupported.material.gradient.numeric-domain-unbounded", failure.message.orEmpty().substringBefore(':'))
        }
        val recovery = Surface(1, 1)
        recovery.canvas { drawRect(rect, Paint(shader = linearStops(2), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), recovery.render().pixels)
    }

    @Test
    fun linearRectDeepOpacityReportsDepthLimitWithoutOverflow() {
        var shader: Shader = linearStops(2)
        repeat(50_000) { shader = Shader.Opacity(shader, 1f) }
        val surface = Surface(1, 1)
        surface.canvas { drawRect(rect, Paint(shader = shader, antiAlias = false)) }
        val captured = assertInstanceOf(SceneCaptureResult.Invalid::class.java, surface.snapshotScene())
        assertEquals("graph-depth-limit", captured.diagnostics.single().code.value)
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("graph-depth-limit", failure.message.orEmpty().substringBefore(':'))
    }

    @Test
    fun linearRectUsesLocalCoordinatesAndMoreThanSixteenStops() {
        val capturedStops = MutableList(17) { indexI32 ->
            GradientStop(
                position = when (indexI32) { 8, 9 -> 0.5f; else -> indexI32 / 16f },
                color = if (indexI32 <= 8) ColorARGB.Red else ColorARGB.Blue,
            )
        }
        val originalStops = capturedStops.toList()
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.White, 1f, BlendMode.SRC_OVER)
        val blend = BlendMode.DIFFERENCE
        val paintAlphaF32 = 64f / 255f
        var ctmF32 = Matrix3x3F32.translation(-8190.5f, -8.5f) * Matrix3x3F32.scaling(1024f, 2f)
        val surface = Surface(4098, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4098f, 1f), Paint(
                shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(4098f, 0f),
                    listOf(GradientStop(0f, destination.color), GradientStop(1f, destination.color))), antiAlias = true,
            ))
            save()
            concat(ctmF32)
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 8f), Paint(
                color = ColorARGB.of(64, 1, 2, 3),
                shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(16f, 0f), capturedStops),
                blendMode = blend, antiAlias = true,
            ))
            restore()
            translate(71f, 93f)
            scale(3f, 7f)
        }
        capturedStops.clear()
        capturedStops += GradientStop(0f, ColorARGB.Green)
        ctmF32 = Matrix3x3F32.scaling(7f, 11f)
        surface.canvas { concat(ctmF32) }

        val expectedSamples = listOf(0 to 7.9990234375f, 1 to 8f, 2 to 8.0009765625f, 4097 to 12f).map { (pixelXI32, localXF32) ->
            val expected = W5cGradientCpuOracle.linearClampSrgb(
                Point2F32(localXF32, 4.5f), Point2F32(0f, 0f), Point2F32(16f, 0f), originalStops,
            ).thenBlend(destination, blend, paintAlphaF32)
            require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
            pixelXI32 to expected
        }
        val result = surface.render()
        expectedSamples.forEach { (pixelXI32, expected) ->
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(pixelXI32 * 4, pixelXI32 * 4 + 4))
        }

        // The same Linear program must interpolate straight sRGB on the integral Rect lane.
        val interpolationStops = listOf(GradientStop(0f, ColorARGB.Black), GradientStop(1f, ColorARGB.White))
        val integral = Surface(9, 1)
        integral.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 9f, 1f), Paint(shader = Shader.SolidColor(ColorARGB.White), antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 9f, 1f), Paint(color = ColorARGB.of(64, 0, 0, 0), blendMode = blend,
                shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(9f, 0f), interpolationStops), antiAlias = false))
        }
        val midpoint = W5cGradientCpuOracle.linearClampSrgb(Point2F32(4.5f, .5f), Point2F32(0f, 0f),
            Point2F32(9f, 0f), interpolationStops).thenBlend(
            destination, blend, paintAlphaF32)
        require(midpoint is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { midpoint.toString() }
        WgslFloatEnvelopeV1Oracle.assertAdmits(midpoint, integral.render().pixels.copyOfRange(16, 20))

        val direct = Surface(1, 1)
        direct.canvas { drawRect(rect, Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
            listOf(GradientStop(0f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue))), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), direct.render().pixels)

        val singleStop = Surface(1, 1)
        singleStop.canvas { drawRect(rect, Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
            listOf(GradientStop(.7f, ColorARGB.Blue))), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), singleStop.render().pixels)
    }

    @Test
    fun recordingStopLimitRefusesTransactionallyBeforeCopy() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))

        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawRect(rect, Paint(shader = linearStops(17))) }
        }

        assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
        assertEquals(16, failure.limitI32)
        assertEquals(17L, failure.requestedI64)
        surface.canvas { drawRect(rect, Paint(shader = Shader.SolidColor(ColorARGB.Red), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0xFFu, 0x00u, 0x00u, 0xFFu), surface.render().pixels)
    }

    @Test
    fun recordingStopLimitCountsSiblingGradientChildren() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))

        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas {
                drawRect(rect, Paint(shader = Shader.Blend(BlendMode.SRC_OVER, linearStops(9), linearStops(9))))
            }
        }

        assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
        assertEquals(18L, failure.requestedI64)
        // A refused sibling must release the first child's pending reservation.
        surface.canvas { drawRect(rect, Paint(shader = linearStops(16))) }
        assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene())
    }

    @Test
    fun recordingStopLimitCountsPriorAppends() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))
        surface.canvas { drawRect(rect, Paint(shader = linearStops(9))) }

        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawRect(rect, Paint(shader = linearStops(8))) }
        }

        assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
        assertEquals(17L, failure.requestedI64)
    }

    @Test
    fun captureStopLimitReturnsTypedDiagnostic() {
        val surface = Surface(1, 1)
        surface.canvas { drawRect(rect, Paint(shader = linearStops(17))) }

        val failure = assertInstanceOf(
            SceneCaptureResult.Invalid::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16)),
        )

        assertEquals("scene-capture-gradient-stops-exceeded", failure.diagnostics.single().code.value)
    }

    @Test
    fun recordingStopLimitCannotBeBypassedByReusingRefusedShader() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))
        val reused = linearStops(9)
        assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawRect(rect, Paint(shader = Shader.Blend(BlendMode.SRC_OVER, reused, linearStops(9)))) }
        }
        surface.canvas { drawRect(rect, Paint(shader = reused)) }

        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawRect(rect, Paint(shader = linearStops(8))) }
        }

        assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
        assertEquals(17L, failure.requestedI64)
    }

    @Test
    fun captureStopLimitPreflightsAllOperationsBeforeStopValidation() {
        val surface = Surface(1, 1)
        val invalidFirst = linearStops(9).copy(stops = List(9) { GradientStop(Float.NaN, ColorARGB.Red) })
        surface.canvas {
            drawRect(rect, Paint(shader = invalidFirst))
            drawRect(rect, Paint(shader = linearStops(8)))
        }

        val failure = assertInstanceOf(
            SceneCaptureResult.Invalid::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16)),
        )

        assertEquals("scene-capture-gradient-stops-exceeded", failure.diagnostics.single().code.value)
    }

    @Test
    fun captureStopLimitPreflightsNestedPicturesBeforeStopValidation() {
        val recorder = PictureRecorder()
        recorder.beginRecording(rect).drawRect(rect, Paint(shader = linearStops(8)))
        val picture = recorder.finishRecordingAsPicture()
        for (asImageFilter in listOf(false, true)) {
            val surface = Surface(1, 1)
            surface.canvas {
                drawRect(rect, Paint(shader = linearStops(9).copy(stops = List(9) { GradientStop(Float.NaN, ColorARGB.Red) })))
                if (asImageFilter) drawRect(rect, Paint(imageFilter = ImageFilter.Picture(picture)))
                else drawPicture(picture)
            }

            val failure = assertInstanceOf(
                SceneCaptureResult.Invalid::class.java,
                surface.snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16)),
            )
            assertEquals("scene-capture-gradient-stops-exceeded", failure.diagnostics.single().code.value)
        }
    }

    @Test
    fun captureStopLimitPreflightsMaskShadersBeforeStopValidation() {
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(rect, Paint(shader = linearStops(9).copy(stops = List(9) { GradientStop(Float.NaN, ColorARGB.Red) })))
            drawRect(rect, Paint(maskFilter = MaskFilter.Shader(linearStops(8))))
        }

        val failure = assertInstanceOf(
            SceneCaptureResult.Invalid::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16)),
        )
        assertEquals("scene-capture-gradient-stops-exceeded", failure.diagnostics.single().code.value)
    }

    private fun linearStops(countI32: Int): Shader.LinearGradient = Shader.LinearGradient(
        start = Point2F32(0f, 0f),
        end = Point2F32(1f, 0f),
        stops = List(countI32) { indexI32 -> GradientStop(indexI32.toFloat() / (countI32 - 1), ColorARGB.Red) },
    )

    private val rect = RectF32.ofLTRB(0f, 0f, 1f, 1f)
}
