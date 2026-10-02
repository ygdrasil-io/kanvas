@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public W7 witnesses for the W4e inverse-AA plus hairline construction seam. */
class W7InverseHairlineAssemblySurfacePixelTest {
    companion object {
        private const val widthI32 = 16
        private const val heightI32 = 10
        private val hole = RectF32.ofLTRB(1f, 1f, 14f, 9f)

        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `inverse AA and default hairline preserve every identity pixel`() {
        val surface = inverseHairlineSurface { canvas ->
            canvas.drawRect(
                RectF32.ofLTRB(2.5f, 2.5f, 7.5f, 7.5f),
                Paint(style = PaintStyle.STROKE, color = ColorARGB.White),
            )
        }

        val result = surface.renderAndRepeat()
        assertEveryPixel(result) { xI32, yI32 ->
            if (xI32 in 2..7 && yI32 in 2..7 &&
                (xI32 == 2 || xI32 == 7 || yI32 == 2 || yI32 == 7)
            ) ColorARGB.White
            else expectedInverseBackground(xI32, yI32)
        }
    }

    @Test
    fun `inverse AA and default hairline preserve every transformed pixel`() {
        val surface = inverseHairlineSurface { canvas ->
            canvas.save()
            canvas.translate(2f, 1f)
            canvas.scale(3f, 1f)
            canvas.drawRect(
                RectF32.ofLTRB(1.5f, 1.5f, 3.5f, 3.5f),
                Paint(style = PaintStyle.STROKE, color = ColorARGB.White),
            )
            canvas.restore()
        }

        val result = surface.renderAndRepeat()
        assertEveryPixel(result) { xI32, yI32 ->
            if (xI32 in 6..12 && yI32 in 2..4 &&
                (xI32 == 6 || xI32 == 12 || yI32 == 2 || yI32 == 4)
            ) ColorARGB.White
            else expectedInverseBackground(xI32, yI32)
        }
    }

    @Test
    fun `AA clip forces the otherwise hard inverse hairline frame without changing interior samples`() {
        val surface = Surface(widthI32, heightI32).also { target -> target.canvas {
            drawRect(fullBounds(), Paint(ColorARGB.Blue, antiAlias = false))
            save()
            clipPath(rectPath(RectF32.ofLTRB(1f, 1f, 15f, 9f)), antiAlias = true)
            drawPath(inversePath(RectF32.ofLTRB(3f, 2f, 13f, 8f)), Paint(ColorARGB.Red, antiAlias = false))
            drawRect(
                RectF32.ofLTRB(5.5f, 5.5f, 9.5f, 9.5f),
                Paint(ColorARGB.White, antiAlias = false, style = PaintStyle.STROKE),
            )
            restore()
        } }

        val result = surface.renderAndRepeat()
        // Every sample is separated from the AA clip boundary; the requested draws are HARD.
        assertPixel(result, 0, 0, ColorARGB.Blue)
        assertPixel(result, 2, 4, ColorARGB.Red)
        assertPixel(result, 14, 4, ColorARGB.Red)
        assertPixel(result, 4, 4, ColorARGB.Blue)
        assertPixel(result, 5, 6, ColorARGB.White)
        assertPixel(result, 9, 6, ColorARGB.White)
        assertPixel(result, 7, 5, ColorARGB.White)
        assertPixel(result, 7, 7, ColorARGB.Blue)
    }

    private fun inverseHairlineSurface(drawHairline: (org.graphiks.kanvas.canvas.Canvas) -> Unit): Surface =
        Surface(widthI32, heightI32).also { target -> target.canvas {
            drawRect(fullBounds(), Paint(ColorARGB.Blue, antiAlias = false))
            drawPath(inversePath(hole), Paint(ColorARGB.Red, antiAlias = true))
            drawHairline(this)
        } }

    private fun inversePath(bounds: RectF32): Path = rectPath(bounds).also { path ->
        path.fillType = FillType.INVERSE_WINDING
    }

    private fun rectPath(bounds: RectF32): Path = Path().apply { addRect(bounds) }

    private fun fullBounds(): RectF32 = RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat())

    private fun expectedInverseBackground(xI32: Int, yI32: Int): ColorARGB =
        if (xI32 in 1..13 && yI32 in 1..8) ColorARGB.Blue else ColorARGB.Red

    private fun Surface.renderAndRepeat(): RenderResult {
        val first = render()
        assertNative(first)
        val second = render()
        assertNative(second)
        assertContentEquals(first.pixels, second.pixels, "second retained Surface frame must be byte-identical")
        return first
    }

    private fun assertNative(result: RenderResult) {
        val trace = "diagnostics=${result.diagnostics.summary()} dispatched=${result.stats.opsDispatched} " +
            "refused=${result.stats.opsRefused} evidence=${result.nativeEvidenceScopeKinds}"
        assertTrue(result.isClean, trace)
        assertTrue(result.diagnostics.isEmpty, trace)
        assertTrue(result.stats.opsDispatched > 0, trace)
        assertEquals(0, result.stats.opsRefused, trace)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(setOf("Render", "Readback")), trace)
    }

    private fun assertEveryPixel(result: RenderResult, expectedAt: (Int, Int) -> ColorARGB) {
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            assertPixel(result, xI32, yI32, expectedAt(xI32, yI32))
        }
    }

    private fun assertPixel(result: RenderResult, xI32: Int, yI32: Int, expected: ColorARGB) {
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        assertContentEquals(
            ubyteArrayOf(
                expected.red.toUByte(), expected.green.toUByte(), expected.blue.toUByte(), expected.alpha.toUByte(),
            ),
            result.pixels.copyOfRange(offsetI32, offsetI32 + 4),
            "pixel ($xI32,$yI32)",
        )
    }
}
