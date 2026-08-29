package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SurfaceSrgbSaveLayerSrcOverOpacityCpuOracleTest {
    @Test
    fun `isolated opaque children receive one group opacity at SrcOver restore`() {
        val pixels = fixture().render(64, 64)

        assertPixel(pixels, 2, 2, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 16, 16, intArrayOf(24, 84, 155, 255))
        assertPixel(pixels, 30, 30, intArrayOf(178, 99, 40, 255))
    }

    @Test
    fun `fixture has literal isolated layer regions`() {
        val pixels = fixture().render(64, 64)
        val colors = mapOf(
            listOf(24, 84, 155, 255) to 0,
            listOf(178, 99, 40, 255) to 1,
            listOf(13, 20, 33, 255) to 2,
        )
        val counts = IntArray(3)
        for (offset in 0 until pixels.size step 4) {
            val rgba = List(4) { channel -> pixels[offset + channel].toInt() and 0xff }
            counts[checkNotNull(colors[rgba])]++
        }
        assertEquals(intArrayOf(560, 896, 2640).toList(), counts.toList())
    }

    @Test
    fun `oracle is closed to the exact 64 by 64 fixture`() {
        assertFailsWith<IllegalArgumentException> { fixture().render(63, 64) }
        assertFailsWith<IllegalArgumentException> { fixture().render(64, 63) }
    }

    private fun fixture() = SurfaceSrgbSaveLayerSrcOverOpacityCpuOracle()

    private fun assertPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        val offset = (y * 64 + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4))
    }
}
