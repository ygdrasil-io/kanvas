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
        assertPixel(oracle("sweep-gradient-partial-angle"), 64, 64, 48, 32, intArrayOf(64, 208, 255, 255))
        assertPixel(oracle("sweep-gradient-partial-angle"), 64, 64, 32, 48, intArrayOf(236, 107, 126, 255))
        assertPixel(oracle("affine-solid-rect"), 64, 64, 15, 16, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("affine-solid-rect"), 64, 64, 16, 16, intArrayOf(242, 135, 46, 255))
        assertPixel(oracle("scissored-radial-gradient"), 64, 64, 19, 12, intArrayOf(0, 0, 0, 0))
        assertPixel(oracle("scissored-radial-gradient"), 64, 64, 20, 12, intArrayOf(54, 83, 191, 255))
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
}
