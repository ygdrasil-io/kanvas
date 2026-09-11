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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals

class W5cGradientSurfacePixelTest {
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
