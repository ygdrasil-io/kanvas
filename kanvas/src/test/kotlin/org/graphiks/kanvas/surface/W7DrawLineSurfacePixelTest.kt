@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.drawLine
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.math.color.ColorARGB
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Public native witness: drawLine must stroke even when Paint defaults to FILL. */
class W7DrawLineSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @ParameterizedTest(name = "drawLine style={0} width={1}")
    @CsvSource("FILL, 0", "FILL, 2", "STROKE_AND_FILL, 0", "STROKE_AND_FILL, 2")
    fun drawLineMatchesExplicitStrokeAndLeavesOpenFillEmpty(style: PaintStyle, width: Float) {
        // Independent hard coverage: width 0 at y=4.5 covers row 4 only;
        // width 2 at y=4 covers rows 3 and 4. The other lanes are shifted by 8 and 16.
        val centerY = if (width == 0f) 4.5f else 4f
        val defaultPaint = Paint(color = ColorARGB.White, strokeWidth = width, antiAlias = false)
        val paint = if (style == PaintStyle.FILL) defaultPaint else defaultPaint.copy(style = style)
        val surface = Surface(40, 32)
        surface.canvas {
            drawPath(line(centerY), paint.copy(style = PaintStyle.STROKE))
            drawPath(line(centerY + 8f), defaultPaint)
            drawLine(4f, centerY + 16f, 28f, centerY + 16f, paint)
        }
        val actual = surface.render()
        assertTrue(actual.isClean, actual.diagnostics.summary())
        assertTrue(actual.diagnostics.isEmpty, actual.diagnostics.summary())
        assertEquals(0, actual.stats.opsRefused)
        assertTrue(actual.stats.opsDispatched > 0)
        assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        assertEquals(PixelFormat.RGBA8, actual.format)

        // First prove the explicit STROKE control is visible, then prove FILL is empty.
        assertPixel(actual, 16, 4, 255)
        assertPixel(actual, 16, 3, if (width == 0f) 0 else 255)
        assertPixel(actual, 16, 2, 0)
        assertPixel(actual, 16, 5, 0)
        assertPixel(actual, 2, 4, 0)
        assertPixel(actual, 30, 4, 0)
        for (y in 9..15) for (x in 0 until 40) assertPixel(actual, x, y, 0)

        assertPixel(actual, 16, 20, 255)
        assertPixel(actual, 16, 19, if (width == 0f) 0 else 255)
        assertPixel(actual, 16, 18, 0)
        assertPixel(actual, 16, 21, 0)
        assertPixel(actual, 2, 20, 0)
        assertPixel(actual, 30, 20, 0)
        for (y in 1..7) for (x in 0 until 40) {
            assertContentEquals(pixel(actual, x, y), pixel(actual, x, y + 16), "lane parity ($x,$y)")
        }
        assertEquals(style, paint.style)
        assertEquals(width, paint.strokeWidth)
        assertEquals(false, paint.antiAlias)
    }

    private fun line(y: Float) = Path { moveTo(4f, y); lineTo(28f, y) }

    private fun pixel(actual: RenderResult, x: Int, y: Int): UByteArray {
        val offset = (y * actual.width + x) * 4
        return actual.pixels.copyOfRange(offset, offset + 4)
    }

    private fun assertPixel(actual: RenderResult, x: Int, y: Int, value: Int) {
        assertContentEquals(UByteArray(4) { value.toUByte() }, pixel(actual, x, y), "pixel ($x,$y)")
    }
}
