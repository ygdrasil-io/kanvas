@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Native pixel contracts for path and paint coordinates through GmCanvas transforms. */
class GmCanvasPathCtmSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun anisotropicHorizontalStrokeKeepsSourceWidth() {
        val actual = renderWhitePathScene { canvas, _, black, _ ->
            canvas.translate(8f, 8f)
            canvas.scale(4f, 2f)
            val path = Path {
                moveTo(4f, 8f)
                lineTo(12f, 8f)
            }
            canvas.drawPath(path, finiteStroke(black, 2f))
        }

        assertPixel(actual, 32, 22, 0, 0, 0, 255)
        assertPixel(actual, 32, 25, 0, 0, 0, 255)
        assertPixel(actual, 32, 21, 255, 255, 255, 255)
        assertPixel(actual, 32, 26, 255, 255, 255, 255)
        assertPixel(actual, 0, 0, 255, 255, 255, 255)
    }

    @Test
    fun anisotropicVerticalStrokeKeepsSourceWidth() {
        val actual = renderWhitePathScene { canvas, _, black, _ ->
            canvas.translate(8f, 8f)
            canvas.scale(4f, 2f)
            val path = Path {
                moveTo(8f, 4f)
                lineTo(8f, 12f)
            }
            canvas.drawPath(path, finiteStroke(black, 2f))
        }

        assertPixel(actual, 36, 24, 0, 0, 0, 255)
        assertPixel(actual, 43, 24, 0, 0, 0, 255)
        assertPixel(actual, 35, 24, 255, 255, 255, 255)
        assertPixel(actual, 44, 24, 255, 255, 255, 255)
        assertPixel(actual, 0, 0, 255, 255, 255, 255)
    }

    @Test
    fun anisotropicHorizontalHairlineKeepsDeviceWidth() {
        val actual = renderWhitePathScene { canvas, _, black, _ ->
            canvas.translate(8f, 8f)
            canvas.scale(4f, 2f)
            val path = Path {
                moveTo(4f, 8.25f)
                lineTo(12f, 8.25f)
            }
            canvas.drawPath(path, finiteStroke(black, 0f))
        }

        assertPixel(actual, 32, 24, 0, 0, 0, 255)
        assertPixel(actual, 32, 23, 255, 255, 255, 255)
        assertPixel(actual, 32, 25, 255, 255, 255, 255)
        assertPixel(actual, 0, 0, 255, 255, 255, 255)
    }

    @Test
    fun anisotropicVerticalHairlineKeepsDeviceWidth() {
        val actual = renderWhitePathScene { canvas, _, black, _ ->
            canvas.translate(8f, 8f)
            canvas.scale(4f, 2f)
            val path = Path {
                moveTo(8.125f, 4f)
                lineTo(8.125f, 12f)
            }
            canvas.drawPath(path, finiteStroke(black, 0f))
        }

        assertPixel(actual, 40, 24, 0, 0, 0, 255)
        assertPixel(actual, 39, 24, 255, 255, 255, 255)
        assertPixel(actual, 41, 24, 255, 255, 255, 255)
        assertPixel(actual, 0, 0, 255, 255, 255, 255)
    }

    @Test
    fun anisotropicStrokeAndFillKeepsSourceOutline() {
        val actual = renderWhitePathScene { canvas, _, black, _ ->
            canvas.translate(8f, 8f)
            canvas.scale(4f, 2f)
            val path = Path {
                moveTo(4f, 4f)
                lineTo(12f, 4f)
                lineTo(12f, 12f)
                lineTo(4f, 12f)
                close()
            }
            canvas.drawPath(
                path,
                finiteStroke(black, 2f).copy(style = PaintStyle.STROKE_AND_FILL),
            )
        }

        assertPixel(actual, 20, 24, 0, 0, 0, 255)
        assertPixel(actual, 32, 14, 0, 0, 0, 255)
        assertPixel(actual, 32, 24, 0, 0, 0, 255)
        assertPixel(actual, 19, 24, 255, 255, 255, 255)
        assertPixel(actual, 32, 13, 255, 255, 255, 255)
        assertPixel(actual, 0, 0, 255, 255, 255, 255)
    }

    @Test
    fun affineFillKeepsSourceGradientCoordinates() {
        val actual = renderWhitePathScene { canvas, _, black, white ->
            canvas.translate(8f, 8f)
            canvas.scale(4f, 2f)
            val path = Path {
                moveTo(0f, 0f)
                lineTo(12f, 0f)
                lineTo(12f, 12f)
                lineTo(0f, 12f)
                close()
            }
            val gradient = Shader.LinearGradient(
                Point2F32(4f, 0f),
                Point2F32(8f, 0f),
                listOf(GradientStop(0f, black), GradientStop(1f, white)),
            )
            canvas.drawPath(path, Paint(shader = gradient, antiAlias = false))
        }

        assertPixel(actual, 16, 20, 0, 0, 0, 255)
        assertPixel(actual, 48, 20, 255, 255, 255, 255)
        assertPixel(actual, 0, 0, 255, 255, 255, 255)
    }

    @Test
    fun nestedPathDrawsRestoreClipAndPreexistingInnerTransform() {
        val red = ColorARGB.fromRGBA(1f, 0f, 0f, 1f)
        val actual = renderWhitePathScene(
            initializeInner = { it.translate(3f, 5f) },
        ) { canvas, _, black, _ ->
            canvas.save()
            canvas.translate(8f, 8f)
            canvas.scale(4f, 2f)
            canvas.clipRect(RectF32(2f, 2f, 6f, 6f))
            canvas.save()
            val path = Path {
                moveTo(0f, 0f)
                lineTo(10f, 0f)
                lineTo(10f, 10f)
                lineTo(0f, 10f)
                close()
            }
            canvas.drawPath(path, Paint(color = black, antiAlias = false))
            canvas.translate(1f, 0f)
            canvas.drawPath(path, Paint(color = black, antiAlias = false))
            canvas.restore()
            canvas.restore()
            val sentinel = Path {
                moveTo(50f, 50f)
                lineTo(52f, 50f)
                lineTo(52f, 52f)
                lineTo(50f, 52f)
                close()
            }
            canvas.drawPath(sentinel, Paint(color = red, antiAlias = false))
        }

        assertPixel(actual, 20, 18, 0, 0, 0, 255)
        assertPixel(actual, 38, 18, 0, 0, 0, 255)
        assertPixel(actual, 18, 18, 255, 255, 255, 255)
        assertPixel(actual, 20, 16, 255, 255, 255, 255)
        assertPixel(actual, 50, 50, 255, 255, 255, 255)
        assertPixel(actual, 53, 55, 255, 0, 0, 255)
    }
}

private fun finiteStroke(color: ColorARGB, width: Float): Paint = Paint(
    color = color,
    style = PaintStyle.STROKE,
    strokeWidth = width,
    strokeCap = StrokeCap.BUTT,
    strokeJoin = StrokeJoin.MITER,
    strokeMiter = 4f,
    antiAlias = false,
)

private fun renderWhitePathScene(
    initializeInner: (Canvas) -> Unit = {},
    draw: (GmCanvas, Canvas, ColorARGB, ColorARGB) -> Unit,
): RenderResult {
    val black = ColorARGB.fromRGBA(0f, 0f, 0f, 1f)
    val white = ColorARGB.fromRGBA(1f, 1f, 1f, 1f)
    val surface = Surface(64, 64)
    val inner = surface.canvas()
    inner.drawRect(
        RectF32(0f, 0f, 64f, 64f),
        Paint(color = white, antiAlias = false),
    )
    initializeInner(inner)
    draw(GmCanvas(inner, 64, 64), inner, black, white)
    val actual = surface.render()
    assertCleanNativeRender(actual)
    return actual
}

private fun assertCleanNativeRender(actual: RenderResult) {
    val trace = "diagnostics=${actual.diagnostics.summary()} dispatched=${actual.stats.opsDispatched} " +
        "refused=${actual.stats.opsRefused} evidence=${actual.nativeEvidenceScopeKinds}"
    assertTrue(actual.isClean, trace)
    assertTrue(actual.diagnostics.isEmpty, trace)
    assertEquals(0, actual.stats.opsRefused, trace)
    assertTrue(actual.stats.opsDispatched > 0, trace)
    assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), trace)
    assertEquals(64, actual.width, trace)
    assertEquals(64, actual.height, trace)
    assertEquals(PixelFormat.RGBA8, actual.format, trace)
    assertEquals(64 * 64 * 4, actual.pixels.size, trace)
}

private fun assertPixel(
    actual: RenderResult,
    x: Int,
    y: Int,
    red: Int,
    green: Int,
    blue: Int,
    alpha: Int,
) {
    val offset = (y * actual.width + x) * 4
    val observed = intArrayOf(
        actual.pixels[offset].toInt(),
        actual.pixels[offset + 1].toInt(),
        actual.pixels[offset + 2].toInt(),
        actual.pixels[offset + 3].toInt(),
    )
    assertTrue(
        observed.contentEquals(intArrayOf(red, green, blue, alpha)),
        "pixel ($x,$y) was ${observed.toList()}",
    )
}
