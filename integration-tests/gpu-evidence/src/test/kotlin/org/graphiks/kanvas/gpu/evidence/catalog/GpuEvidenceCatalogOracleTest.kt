package org.graphiks.kanvas.gpu.evidence.catalog

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GpuEvidenceCatalogOracleTest {
    @Test
    fun `translucent overlap oracle matches literal premultiplied src-over pixels`() {
        val pixels = oracle("translucent-card-overlap")

        assertPixel(pixels, 64, 64, 2, 2, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 12, 12, intArrayOf(46, 94, 142, 255))
        assertPixel(pixels, 64, 64, 50, 50, intArrayOf(93, 48, 33, 255))
        assertPixel(pixels, 64, 64, 30, 30, intArrayOf(98, 81, 105, 255))
    }

    @Test
    fun `scissor oracle leaves clipped pixels untouched and paints literal intersection`() {
        val pixels = oracle("scissor-overlay")

        assertPixel(pixels, 64, 64, 10, 10, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 64, 64, 20, 20, intArrayOf(31, 115, 209, 255))
        assertPixel(pixels, 64, 64, 30, 30, intArrayOf(242, 135, 46, 255))
    }

    @Test
    fun `stroke oracle paints only the four literal coverage bands`() {
        val pixels = oracle("stroke-rect-outline")
        val background = intArrayOf(13, 20, 33, 255)
        val stroke = intArrayOf(242, 135, 46, 255)

        assertPixel(pixels, 64, 64, 12, 12, background)
        assertPixel(pixels, 64, 64, 30, 30, background)
        assertPixel(pixels, 64, 64, 14, 14, stroke)
        assertPixel(pixels, 64, 64, 14, 46, stroke)
        assertPixel(pixels, 64, 64, 14, 30, stroke)
        assertPixel(pixels, 64, 64, 46, 30, stroke)
    }

    @Test
    fun `gradient oracles preserve literal clamp endpoints and transparent exterior`() {
        assertPixel(oracle("linear-gradient-lanes"), 64, 64, 7, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("linear-gradient-lanes"), 64, 64, 8, 16, intArrayOf(255, 56, 56, 255))
        assertPixel(oracle("linear-gradient-lanes"), 64, 64, 32, 16, intArrayOf(189, 90, 192, 255))
        assertPixel(oracle("linear-gradient-lanes"), 64, 64, 55, 16, intArrayOf(56, 112, 255, 255))

        assertPixel(oracle("radial-swatch"), 64, 64, 7, 8, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("radial-swatch"), 64, 64, 32, 32, intArrayOf(255, 232, 72, 255))
        assertPixel(oracle("radial-swatch"), 64, 64, 44, 32, intArrayOf(188, 176, 149, 255))

        assertPixel(oracle("sweep-disk"), 64, 64, 7, 8, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("sweep-disk"), 64, 64, 48, 32, intArrayOf(255, 64, 64, 255))
        assertPixel(oracle("sweep-disk"), 64, 64, 32, 48, intArrayOf(226, 122, 146, 255))
    }

    @Test
    fun `wave two oracles preserve hand-derived gradient affine and clip pixels`() {
        assertPixel(oracle("linear-gradient-three-stops"), 64, 64, 20, 16, intArrayOf(189, 167, 95, 255))
        assertPixel(oracle("linear-gradient-three-stops"), 64, 64, 32, 16, intArrayOf(56, 218, 125, 255))
        assertPixel(oracle("sweep-gradient-partial-angle"), 64, 64, 48, 32, intArrayOf(255, 64, 64, 255))
        assertPixel(oracle("sweep-gradient-partial-angle"), 64, 64, 32, 48, intArrayOf(236, 107, 126, 255))
        // Pixel centres make (16,17) cross the sloped left edge: its top-left
        // corner maps outside, while its centre maps to local x = 8.125.
        assertPixel(oracle("affine-solid-rect"), 64, 64, 15, 15, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("affine-solid-rect"), 64, 64, 15, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("affine-solid-rect"), 64, 64, 16, 16, intArrayOf(242, 135, 46, 255))
        assertPixel(oracle("affine-solid-rect"), 64, 64, 16, 17, intArrayOf(242, 135, 46, 255))
        // The right edge is half-open: at this row, the pixel centre maps to
        // local x = 40.125, so it remains clear even though its corner is in.
        assertPixel(oracle("affine-solid-rect"), 64, 64, 48, 17, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("scissored-radial-gradient"), 64, 64, 19, 12, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("scissored-radial-gradient"), 64, 64, 20, 12, intArrayOf(54, 83, 191, 255))
    }

    @Test
    fun `rrect and drrect oracles preserve literal device coverage and fill counts`() {
        val background = intArrayOf(13, 20, 33, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val scaledRRect = oracle("scaled-solid-rrect")
        val drrect = oracle("solid-drrect-hole")

        assertPixel(scaledRRect, 64, 64, 15, 16, background)
        assertPixel(scaledRRect, 64, 64, 16, 16, background)
        assertPixel(scaledRRect, 64, 64, 24, 16, orange)
        assertPixel(scaledRRect, 64, 64, 32, 32, orange)
        assertPixel(scaledRRect, 64, 64, 47, 47, background)
        assertEquals(996, fillPixelCount(scaledRRect, orange))

        assertPixel(drrect, 64, 64, 8, 8, background)
        assertPixel(drrect, 64, 64, 12, 12, blue)
        assertPixel(drrect, 64, 64, 20, 20, blue)
        assertPixel(drrect, 64, 64, 32, 32, background)
        assertPixel(drrect, 64, 64, 44, 32, blue)
        assertPixel(drrect, 64, 64, 55, 55, background)
        assertEquals(1692, fillPixelCount(drrect, blue))
    }

    @Test
    fun `advanced rrect oracles preserve independent corner coverage and fill counts`() {
        val background = intArrayOf(13, 20, 33, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val asymmetric = oracle("asymmetric-solid-rrect")
        val ellipse = oracle("ellipse-solid-rrect")
        val drrect = oracle("asymmetric-solid-drrect-hole")

        assertPixel(asymmetric, 64, 64, 10, 8, background)
        assertPixel(asymmetric, 64, 64, 11, 8, orange)
        assertPixel(asymmetric, 64, 64, 50, 8, orange)
        assertPixel(asymmetric, 64, 64, 51, 8, background)
        assertPixel(asymmetric, 64, 64, 10, 55, background)
        assertPixel(asymmetric, 64, 64, 11, 55, orange)
        assertPixel(asymmetric, 64, 64, 49, 55, orange)
        assertPixel(asymmetric, 64, 64, 50, 55, background)
        assertEquals(2265, fillPixelCount(asymmetric, orange))

        assertPixel(ellipse, 64, 64, 25, 20, background)
        assertPixel(ellipse, 64, 64, 26, 20, blue)
        assertPixel(ellipse, 64, 64, 37, 20, blue)
        assertPixel(ellipse, 64, 64, 38, 20, background)
        assertPixel(ellipse, 64, 64, 12, 32, blue)
        assertPixel(ellipse, 64, 64, 52, 32, background)
        assertEquals(764, fillPixelCount(ellipse, blue))

        assertPixel(drrect, 64, 64, 20, 20, blue)
        assertPixel(drrect, 64, 64, 21, 20, background)
        assertPixel(drrect, 64, 64, 41, 20, background)
        assertPixel(drrect, 64, 64, 42, 20, blue)
        assertPixel(drrect, 64, 64, 20, 43, blue)
        assertPixel(drrect, 64, 64, 21, 43, background)
        assertPixel(drrect, 64, 64, 41, 43, background)
        assertPixel(drrect, 64, 64, 42, 43, blue)
        assertEquals(1889, fillPixelCount(drrect, blue))
    }

    @Test
    fun `path fill oracles preserve literal winding and inverse coverage`() {
        val background = intArrayOf(13, 20, 33, 255)
        val orange = intArrayOf(242, 135, 46, 255)
        val blue = intArrayOf(31, 115, 209, 255)
        val green = intArrayOf(56, 220, 120, 255)
        val triangle = oracle("solid-triangle-path")
        val concave = oracle("solid-concave-path")
        val evenOdd = oracle("even-odd-path-hole")
        val windingHole = oracle("winding-path-hole")
        val inverseWinding = oracle("inverse-winding-triangle-path")
        val inverseEvenOdd = oracle("inverse-even-odd-path-hole")

        assertPixel(triangle, 64, 64, 8, 8, orange)
        assertPixel(triangle, 64, 64, 55, 8, background)
        assertPixel(triangle, 64, 64, 31, 31, orange)
        assertPixel(triangle, 64, 64, 32, 31, background)
        assertEquals(1128, fillPixelCount(triangle, orange))

        assertPixel(concave, 64, 64, 10, 10, blue)
        assertPixel(concave, 64, 64, 40, 30, background)
        assertPixel(concave, 64, 64, 40, 44, blue)
        assertEquals(1920, fillPixelCount(concave, blue))

        assertPixel(evenOdd, 64, 64, 10, 10, green)
        assertPixel(evenOdd, 64, 64, 22, 20, background)
        assertPixel(evenOdd, 64, 64, 30, 30, background)
        assertPixel(evenOdd, 64, 64, 44, 30, green)
        assertEquals(1776, fillPixelCount(evenOdd, green))

        assertPixel(windingHole, 64, 64, 10, 10, blue)
        assertPixel(windingHole, 64, 64, 30, 30, background)
        assertPixel(windingHole, 64, 64, 44, 30, blue)
        assertEquals(1776, fillPixelCount(windingHole, blue))

        assertPixel(inverseWinding, 64, 64, 4, 4, orange)
        assertPixel(inverseWinding, 64, 64, 8, 8, background)
        assertPixel(inverseWinding, 64, 64, 31, 31, background)
        assertPixel(inverseWinding, 64, 64, 55, 8, orange)
        assertEquals(2968, fillPixelCount(inverseWinding, orange))

        assertPixel(inverseEvenOdd, 64, 64, 4, 4, green)
        assertPixel(inverseEvenOdd, 64, 64, 10, 10, background)
        assertPixel(inverseEvenOdd, 64, 64, 30, 30, green)
        assertPixel(inverseEvenOdd, 64, 64, 44, 30, background)
        assertEquals(2320, fillPixelCount(inverseEvenOdd, green))
    }

    private fun oracle(id: String): ByteArray = assertNotNull(
        GpuEvidenceCatalog.renderCases.firstOrNull { it.descriptor.id.value == id }?.oracle,
    ).render(64, 64)

    private fun assertPixel(pixels: ByteArray, width: Int, height: Int, x: Int, y: Int, expected: IntArray) {
        require(x in 0 until width && y in 0 until height)
        val offset = (y * width + x) * 4
        assertEquals(4, expected.size)
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }

    private fun fillPixelCount(pixels: ByteArray, color: IntArray): Int =
        pixels.asList().chunked(4).count { pixel -> pixel.map { it.toInt() and 0xff } == color.toList() }
}
