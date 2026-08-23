package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class GradientCpuOracleTest {
    @Test
    fun `linear gradient clamps before start and after end and interpolates the midpoint`() {
        val pixels = GradientCpuOracle.linear(
            drawBounds = Rect.fromLTRB(0f, 0f, 5f, 1f),
            start = Point(1.5f, 0.5f),
            end = Point(3.5f, 0.5f),
            stops = listOf(GradientStop(0f, Color.BLACK), GradientStop(1f, Color.WHITE)),
        ).render(5, 1)

        assertPixel(pixels, 5, 0, 0, intArrayOf(0, 0, 0, 255))
        assertPixel(pixels, 5, 1, 0, intArrayOf(0, 0, 0, 255))
        assertPixel(pixels, 5, 2, 0, intArrayOf(128, 128, 128, 255))
        assertPixel(pixels, 5, 3, 0, intArrayOf(255, 255, 255, 255))
        assertPixel(pixels, 5, 4, 0, intArrayOf(255, 255, 255, 255))
    }

    @Test
    fun `radial gradient uses pixel center distance divided by radius`() {
        val pixels = GradientCpuOracle.radial(
            drawBounds = Rect.fromLTRB(0f, 0f, 5f, 5f),
            center = Point(2.5f, 2.5f),
            radius = 2f,
            stops = listOf(GradientStop(0f, Color.BLACK), GradientStop(1f, Color.WHITE)),
        ).render(5, 5)

        assertPixel(pixels, 5, 2, 2, intArrayOf(0, 0, 0, 255))
        assertPixel(pixels, 5, 3, 2, intArrayOf(128, 128, 128, 255))
        assertPixel(pixels, 5, 4, 2, intArrayOf(255, 255, 255, 255))
    }

    @Test
    fun `sweep gradient uses clockwise screen coordinates and clamps outside the requested angle span`() {
        val pixels = GradientCpuOracle.sweep(
            drawBounds = Rect.fromLTRB(0f, 0f, 5f, 5f),
            center = Point(2.5f, 2.5f),
            startAngle = 0f,
            endAngle = 180f,
            stops = listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE)),
        ).render(5, 5)

        assertPixel(pixels, 5, 3, 2, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 5, 2, 3, intArrayOf(128, 0, 128, 255))
        assertPixel(pixels, 5, 1, 2, intArrayOf(0, 0, 255, 255))
        assertPixel(pixels, 5, 2, 1, intArrayOf(0, 0, 255, 255))
    }

    @Test
    fun `degenerate gradient geometry selects the start stop`() {
        val bounds = Rect.fromLTRB(0f, 0f, 1f, 1f)
        val expected = intArrayOf(255, 0, 0, 255)

        listOf(
            GradientCpuOracle.linear(bounds, Point(0.5f, 0.5f), Point(0.5f, 0.5f), listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE))),
            GradientCpuOracle.radial(bounds, Point(0.5f, 0.5f), 0f, listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE))),
            GradientCpuOracle.sweep(bounds, Point(0.5f, 0.5f), 90f, 90f, listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE))),
        ).forEach { oracle -> assertPixel(oracle.render(1, 1), 1, 0, 0, expected) }
    }

    private fun assertPixel(pixels: ByteArray, width: Int, x: Int, y: Int, expected: IntArray) {
        val offset = (y * width + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }
}
