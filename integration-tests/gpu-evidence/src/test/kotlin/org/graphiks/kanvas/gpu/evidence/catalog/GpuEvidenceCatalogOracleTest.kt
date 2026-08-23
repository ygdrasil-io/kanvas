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
        assertPixel(pixels, 64, 64, 12, 12, intArrayOf(38, 73, 112, 255))
        assertPixel(pixels, 64, 64, 50, 50, intArrayOf(70, 41, 32, 255))
        assertPixel(pixels, 64, 64, 30, 30, intArrayOf(82, 68, 71, 255))
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

    private fun oracle(id: String): ByteArray = assertNotNull(
        GpuEvidenceCatalog.cases.firstOrNull { it.descriptor.id.value == id }?.oracle,
    ).render(64, 64)

    private fun assertPixel(pixels: ByteArray, width: Int, height: Int, x: Int, y: Int, expected: IntArray) {
        require(x in 0 until width && y in 0 until height)
        val offset = (y * width + x) * 4
        assertEquals(4, expected.size)
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }
}
