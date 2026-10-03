@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Native drawLine semantics through the GM adapter's translation and clip envelope. */
class W7GmDrawLineSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @ParameterizedTest(name = "GM drawLine style={0} width={1}")
    @CsvSource("FILL, 0", "FILL, 2", "STROKE_AND_FILL, 0", "STROKE_AND_FILL, 2")
    fun translatedClippedDrawLineMatchesExplicitStrokeAndLeavesOpenFillEmpty(style: PaintStyle, width: Float) {
        // The +3,+2 translation maps the clip to device [11,27) x [2,26).
        // Width 0 covers device row 6 only; width 2 covers rows 5 and 6.
        val centerY = if (width == 0f) 4.5f else 4f
        val defaultPaint = Paint(color = ColorARGB.White, strokeWidth = width, antiAlias = false)
        val paint = if (style == PaintStyle.FILL) defaultPaint else defaultPaint.copy(style = style)
        val surface = Surface(40, 32)
        val inner = surface.canvas()
        val canvas = GmCanvas(inner, 40, 32)
        canvas.save()
        canvas.translate(3f, 2f)
        canvas.clipRect(RectF32.ofLTRB(8f, 0f, 24f, 24f))
        canvas.drawPath(line(centerY), paint.copy(style = PaintStyle.STROKE))
        // An open two-point FILL has zero area at either y; integer collapsed cover bounds
        // are a separately tracked preparation issue, independent of drawLine semantics.
        canvas.drawPath(line(12.5f), defaultPaint)
        canvas.drawLine(4f, centerY + 16f, 28f, centerY + 16f, paint)
        canvas.restore()
        // A following inner draw must see neither the GM clip nor the GM translation.
        inner.drawRect(RectF32.ofLTRB(1f, 28f, 3f, 30f), Paint(color = ColorARGB.White, antiAlias = false))
        val actual = surface.render()
        assertTrue(actual.isClean, actual.diagnostics.summary())
        assertTrue(actual.diagnostics.isEmpty, actual.diagnostics.summary())
        assertEquals(0, actual.stats.opsRefused)
        assertTrue(actual.stats.opsDispatched > 0)
        // Scope names are route-specific; both Surface routes expose actual native draw/pipeline counts.
        // Native submission/readback guards and the pixel controls below remain required.
        println("W7_GM_DRAWLINE_NATIVE style=$style width=$width stats=${actual.stats} scopes=${actual.nativeEvidenceScopeKinds}")
        assertTrue(actual.stats.drawCallCount > 0, "native draw count: ${actual.stats}")
        assertTrue(actual.stats.pipelineCount > 0, "native pipeline count: ${actual.stats}")
        assertEquals(PixelFormat.RGBA8, actual.format)

        assertPixel(actual, 16, 6, 255)
        assertPixel(actual, 16, 5, if (width == 0f) 0 else 255)
        assertPixel(actual, 16, 4, 0)
        assertPixel(actual, 16, 7, 0)
        assertPixel(actual, 10, 6, 0)
        assertPixel(actual, 11, 6, 255)
        assertPixel(actual, 26, 6, 255)
        assertPixel(actual, 27, 6, 0)
        for (y in 11..17) for (x in 0 until 40) assertPixel(actual, x, y, 0)
        assertPixel(actual, 1, 28, 255)
        assertPixel(actual, 4, 30, 0)

        assertPixel(actual, 16, 22, 255)
        assertPixel(actual, 16, 21, if (width == 0f) 0 else 255)
        assertPixel(actual, 16, 20, 0)
        assertPixel(actual, 16, 23, 0)
        assertPixel(actual, 10, 22, 0)
        assertPixel(actual, 11, 22, 255)
        assertPixel(actual, 26, 22, 255)
        assertPixel(actual, 27, 22, 0)
        for (y in 3..9) for (x in 0 until 40) {
            assertArrayEquals(pixel(actual, x, y), pixel(actual, x, y + 16), "lane parity ($x,$y)")
        }
        assertEquals(style, paint.style)
        assertEquals(width, paint.strokeWidth)
        assertEquals(false, paint.antiAlias)
    }

    private fun line(y: Float) = Path { moveTo(4f, y); lineTo(28f, y) }

    private fun pixel(actual: RenderResult, x: Int, y: Int): IntArray {
        val offset = (y * actual.width + x) * 4
        return IntArray(4) { actual.pixels[offset + it].toInt() }
    }

    private fun assertPixel(actual: RenderResult, x: Int, y: Int, value: Int) {
        assertArrayEquals(intArrayOf(value, value, value, value), pixel(actual, x, y), "pixel ($x,$y)")
    }
}
