@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Native public witnesses for the hard, integer Rect hairline contract. */
class W7RectHairlineSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `integerRectHairlineCoversEveryExpectedPixel`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        // This literal oracle is defined before Surface creation and does not reuse renderer geometry.
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBRRRRBB", "BBRBBRBB",
            "BBRBBRBB", "BBRRRRBB", "BBBBBBBB", "BBBBBBBB",
        )

        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 2f, 5f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }

        val first = surface.render()
        assertPixelTable(expected, blue, red, first)
        val second = surface.render()
        assertPixelTable(expected, blue, red, second)
        assertContentEquals(first.pixels, second.pixels)
    }

    @Test
    fun `translucentCornersAreCompositedOnce`() {
        val black = ColorARGB.Black
        val halfRed = ColorARGB.of(128, 255, 0, 0)
        val once = ColorARGB.of(255, 188, 0, 0)
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBRRRRBB", "BBRBBRBB",
            "BBRBBRBB", "BBRRRRBB", "BBBBBBBB", "BBBBBBBB",
        )
        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(black, antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 2f, 5f, 5f),
                Paint(halfRed, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(surface, expected, black, once)
    }

    @Test
    fun `clippingDoesNotMoveHairlineEdges`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "RRRRRRBB", "BBBBBRBB",
            "BBBBBRBB", "RRRRRRBB", "BBBBBBBB", "BBBBBBBB",
        )
        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(-1f, 2f, 5f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(surface, expected, blue, red)

        val interiorClip = Surface(8, 8)
        interiorClip.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            clipRect(RectF32.ofLTRB(2f, 0f, 8f, 8f), antiAlias = false)
            drawRect(RectF32.ofLTRB(1f, 2f, 6f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(
            interiorClip,
            listOf(
                "BBBBBBBB", "BBBBBBBB", "BBRRRRRB", "BBBBBBRB",
                "BBBBBBRB", "BBRRRRRB", "BBBBBBBB", "BBBBBBBB",
            ),
            blue,
            red,
        )
    }

    @Test
    fun `integerScaledHairlineStaysOneDevicePixel`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBRRRR",
            "BBBRBBR", "BBBRBBR", "BBBRRRR", "BBBBBBBB",
        )
        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            scale(3f, 3f)
            drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(surface, expected, blue, red)
    }

    @Test
    fun `thinAndFullyClippedHairlinesPreserveDestination`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val thin = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBRRBBBB", "BBRRBBBB",
            "BBRRBBBB", "BBRRBBBB", "BBBBBBBB", "BBBBBBBB",
        )
        val thinSurface = Surface(8, 8)
        thinSurface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(thinSurface, thin, blue, red)

        val fullyClipped = Surface(8, 8)
        fullyClipped.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            clipRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), antiAlias = false)
            drawRect(RectF32.ofLTRB(2f, 2f, 5f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(fullyClipped, List(8) { "BBBBBBBB" }, blue, red)
    }
}

private fun assertRepeatedPixelTable(surface: Surface, expected: List<String>, blue: ColorARGB, red: ColorARGB) {
    val first = surface.render()
    assertPixelTable(expected, blue, red, first)
    val second = surface.render()
    assertPixelTable(expected, blue, red, second)
    assertContentEquals(first.pixels, second.pixels)
}

private fun assertPixelTable(expected: List<String>, blue: ColorARGB, red: ColorARGB, actual: RenderResult) {
    expected.forEachIndexed { y, row -> row.forEachIndexed { x, marker ->
        val color = if (marker == 'R') red else blue
        val offset = (y * 8 + x) * 4
        assertContentEquals(
            ubyteArrayOf(color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), color.alpha.toUByte()),
            actual.pixels.copyOfRange(offset, offset + 4),
            "pixel ($x,$y)",
        )
    } }
    assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), actual.nativeEvidenceScopeKinds.toString())
}
