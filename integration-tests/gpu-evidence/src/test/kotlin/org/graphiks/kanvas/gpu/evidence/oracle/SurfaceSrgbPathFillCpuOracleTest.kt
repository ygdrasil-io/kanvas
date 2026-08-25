package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SurfaceSrgbPathFillCpuOracleTest {
    @Test
    fun `winding triangle samples pixel centers without edge ambiguity`() {
        val pixels = triangle.render(64, 64)

        assertPixel(pixels, 8, 8, ORANGE)
        assertPixel(pixels, 54, 8, ORANGE)
        assertPixel(pixels, 55, 8, BACKGROUND)
        assertPixel(pixels, 31, 31, ORANGE)
        assertPixel(pixels, 32, 31, BACKGROUND)
        assertPixel(pixels, 8, 54, ORANGE)
        assertPixel(pixels, 9, 54, BACKGROUND)
        assertEquals(1128, count(pixels, ORANGE))
    }

    @Test
    fun `winding concave contour preserves the rectangular notch`() {
        val pixels = concaveOracle().render(64, 64)

        assertPixel(pixels, 10, 10, BLUE)
        assertPixel(pixels, 40, 10, BLUE)
        assertPixel(pixels, 40, 30, BACKGROUND)
        assertPixel(pixels, 40, 34, BACKGROUND)
        assertPixel(pixels, 40, 44, BLUE)
        assertPixel(pixels, 4, 4, BACKGROUND)
        assertEquals(1920, count(pixels, BLUE))
    }

    @Test
    fun `winding keeps nested same orientation contours filled`() {
        val pixels = windingNestedOracle().render(64, 64)

        assertPixel(pixels, 30, 30, GREEN)
        assertPixel(pixels, 4, 4, BACKGROUND)
    }

    @Test
    fun `even odd toggles membership across both same orientation contours`() {
        val pixels = evenOddOracle().render(64, 64)

        assertPixel(pixels, 10, 10, GREEN)
        assertPixel(pixels, 21, 30, GREEN)
        assertPixel(pixels, 22, 20, BACKGROUND)
        assertPixel(pixels, 30, 30, BACKGROUND)
        assertPixel(pixels, 44, 30, GREEN)
        assertEquals(1776, count(pixels, GREEN))
    }

    @Test
    fun `oracle validates colors contours points and target dimensions`() {
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbPathFillCpuOracle(intArrayOf(0, 0, 0), ORANGE, validContours(), SurfaceSrgbPathFillCpuOracle.FillRule.Winding)
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbPathFillCpuOracle(BACKGROUND, intArrayOf(0, 0, 0, 256), validContours(), SurfaceSrgbPathFillCpuOracle.FillRule.Winding)
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbPathFillCpuOracle(BACKGROUND, ORANGE, emptyList(), SurfaceSrgbPathFillCpuOracle.FillRule.Winding)
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(point(0f, 0f), point(1f, 0f)))
        }
        assertFailsWith<IllegalArgumentException> {
            point(Float.NaN, 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbPathFillCpuOracle.Contour(listOf(point(0f, 0f), point(1f, 0f), point(1f, 1f), point(1f, 1f)))
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbPathFillCpuOracle(BACKGROUND, ORANGE, validContours(), SurfaceSrgbPathFillCpuOracle.FillRule.Winding)
                .render(63, 64)
        }
    }

    private val triangle = SurfaceSrgbPathFillCpuOracle(
        BACKGROUND,
        ORANGE,
        listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(
                listOf(point(8f, 8f), point(56f, 8f), point(8f, 55f)),
            ),
        ),
        SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun concaveOracle() = SurfaceSrgbPathFillCpuOracle(
        BACKGROUND,
        BLUE,
        listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(
                listOf(
                    point(8f, 8f), point(56f, 8f), point(56f, 24f), point(32f, 24f),
                    point(32f, 40f), point(56f, 40f), point(56f, 56f), point(8f, 56f),
                ),
            ),
        ),
        SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun windingNestedOracle() = SurfaceSrgbPathFillCpuOracle(
        BACKGROUND,
        GREEN,
        listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(
                listOf(point(8f, 8f), point(56f, 8f), point(56f, 56f), point(8f, 56f)),
            ),
            SurfaceSrgbPathFillCpuOracle.Contour(
                listOf(point(22f, 20f), point(44f, 20f), point(44f, 44f), point(22f, 44f)),
            ),
        ),
        SurfaceSrgbPathFillCpuOracle.FillRule.Winding,
    )

    private fun evenOddOracle() = SurfaceSrgbPathFillCpuOracle(
        BACKGROUND,
        GREEN,
        listOf(
            SurfaceSrgbPathFillCpuOracle.Contour(
                listOf(point(8f, 8f), point(56f, 8f), point(56f, 56f), point(8f, 56f)),
            ),
            SurfaceSrgbPathFillCpuOracle.Contour(
                listOf(point(22f, 20f), point(44f, 20f), point(44f, 44f), point(22f, 44f)),
            ),
        ),
        SurfaceSrgbPathFillCpuOracle.FillRule.EvenOdd,
    )

    private fun validContours() = listOf(
        SurfaceSrgbPathFillCpuOracle.Contour(listOf(point(8f, 8f), point(16f, 8f), point(8f, 16f))),
    )

    private fun point(x: Float, y: Float) = SurfaceSrgbPathFillCpuOracle.Point(x, y)

    private fun count(pixels: ByteArray, color: IntArray): Int =
        pixels.asSequence().chunked(4).count { it.map(Byte::toInt).map { value -> value and 0xff } == color.toList() }

    private fun assertPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        val offset = (y * 64 + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4))
    }

    private companion object {
        val BACKGROUND = intArrayOf(13, 20, 33, 255)
        val ORANGE = intArrayOf(242, 135, 46, 255)
        val BLUE = intArrayOf(31, 115, 209, 255)
        val GREEN = intArrayOf(56, 220, 120, 255)
    }
}
