@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Public native witnesses for hard-edge stroke coverage with a disjoint clip sibling. */
class W7HardStrokeClipAssemblySurfacePixelTest {
    companion object {
        private const val widthI32 = 64
        private const val heightI32 = 40

        private val purple = ColorARGB.of(255, 221, 160, 221)
        private val green = ColorARGB.of(255, 160, 221, 160)
        private val blue = ColorARGB.of(255, 0, 0, 255)
        private val black = ColorARGB.of(255, 0, 0, 0)

        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `hard rect hairline preserves binary coverage with disjoint clip sibling`(): Unit {
        val surface = strokeSurface { canvas ->
            canvas.drawRect(
                RectF32.ofLTRB(8.5f, 8.5f, 24.5f, 24.5f),
                Paint(color = blue, style = PaintStyle.STROKE, strokeWidth = 0f, antiAlias = false),
            )
        }

        val result = surface.renderAndRepeat()
        assertStrokeLiterals(result)
    }

    @Test
    fun `explicit closed path hairline preserves binary coverage with disjoint clip sibling`(): Unit {
        val surface = strokeSurface { canvas ->
            canvas.drawPath(
                closedRectPath(8.5f, 8.5f, 24.5f, 24.5f),
                Paint(color = blue, style = PaintStyle.STROKE, strokeWidth = 0f, antiAlias = false),
            )
        }

        val result = surface.renderAndRepeat()
        assertStrokeLiterals(result)
    }

    @Test
    fun `opaque hard triangle keeps fill geometry under a later blue rect and disjoint clip sibling`(): Unit {
        val surface = blackTriangleSurface(ColorARGB.Red)

        val result = surface.renderAndRepeat()
        assertPixel(result, 20, 20, black)
        assertPixel(result, 10, 16, ColorARGB.Red)
        assertPixel(result, 10, 10, blue)
        assertPixel(result, 44, 16, green)
        assertPixel(result, 52, 16, black)
    }

    @Test
    fun `half alpha white hard triangle composes once in linear domain`(): Unit {
        val halfAlphaWhite = ColorARGB.of(128, 255, 255, 255)
        val surface = blackTriangleSurface(halfAlphaWhite)

        val result = surface.renderAndRepeat()
        assertPixel(result, 20, 20, black)
        assertPixel(result, 10, 16, ColorARGB.of(255, 188, 188, 188))
        assertPixel(result, 10, 10, blue)
        assertPixel(result, 44, 16, green)
        assertPixel(result, 52, 16, black)
    }

    private fun strokeSurface(drawStroke: (org.graphiks.kanvas.canvas.Canvas) -> Unit): Surface =
        Surface(widthI32, heightI32, config = RenderConfig(compositionDomain = CompositionDomain.LINEAR)).also { target ->
            target.canvas {
                drawRect(fullBounds(), Paint(color = purple, antiAlias = true))
                drawStroke(this)
                drawDisjointClipSibling()
            }
        }

    private fun blackTriangleSurface(triangleColor: ColorARGB): Surface =
        Surface(widthI32, heightI32, config = RenderConfig(compositionDomain = CompositionDomain.LINEAR)).also { target ->
            target.canvas {
                drawRect(fullBounds(), Paint(color = black, antiAlias = true))
                drawPath(
                    Path().apply {
                        moveTo(8f, 8f)
                        lineTo(24f, 8f)
                        lineTo(8f, 24f)
                        close()
                    },
                    Paint(color = triangleColor, antiAlias = false),
                )
                drawRect(RectF32.ofLTRB(9f, 9f, 13f, 13f), Paint(color = blue, antiAlias = false))
                drawDisjointClipSibling()
            }
        }

    private fun org.graphiks.kanvas.canvas.Canvas.drawDisjointClipSibling() {
        save()
        clipPath(rectPath(40f, 8f, 56f, 24f), ClipOp.INTERSECT, antiAlias = false)
        clipPath(rectPath(48f, 8f, 56f, 24f), ClipOp.DIFFERENCE, antiAlias = false)
        drawRect(
            RectF32.ofLTRB(40f, 8f, 56f, 24f),
            Paint(color = green, antiAlias = false),
        )
        restore()
    }

    private fun assertStrokeLiterals(result: RenderResult) {
        // Half-integer bounds avoid edge-tie ambiguity. Samples are fixed literals, not renderer-derived geometry.
        assertPixel(result, 16, 16, purple)
        assertPixel(result, 8, 16, blue)
        assertPixel(result, 24, 16, blue)
        assertPixel(result, 16, 8, blue)
        assertPixel(result, 16, 24, blue)
        assertPixel(result, 7, 16, purple)
        assertPixel(result, 25, 16, purple)
        assertPixel(result, 16, 7, purple)
        assertPixel(result, 16, 25, purple)
        assertPixel(result, 44, 16, green)
        assertPixel(result, 52, 16, purple)
    }

    private fun rectPath(left: Float, top: Float, right: Float, bottom: Float): Path =
        Path().apply { addRect(RectF32.ofLTRB(left, top, right, bottom)) }

    private fun closedRectPath(left: Float, top: Float, right: Float, bottom: Float): Path = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }

    private fun fullBounds(): RectF32 = RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat())

    private fun Surface.renderAndRepeat(): RenderResult {
        val first = render()
        assertNative(first)
        val second = render()
        assertNative(second)
        assertArrayEquals(first.pixels.toByteArray(), second.pixels.toByteArray(),
            "second retained Surface frame must be byte-identical")
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

    private fun assertPixel(result: RenderResult, xI32: Int, yI32: Int, expected: ColorARGB) {
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        assertArrayEquals(
            byteArrayOf(expected.red.toByte(), expected.green.toByte(), expected.blue.toByte(), expected.alpha.toByte()),
            result.pixels.copyOfRange(offsetI32, offsetI32 + 4).toByteArray(),
            "pixel ($xI32,$yI32)",
        )
    }
}
