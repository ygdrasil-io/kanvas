package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SurfaceSrgbSrcOverCpuOracleTest {
    @Test
    fun `oracle is closed to the exact 64 by 64 fixture`() {
        assertEquals(64, SurfaceSrgbSrcOverCpuOracle.WIDTH)
        assertEquals(64, SurfaceSrgbSrcOverCpuOracle.HEIGHT)
        assertFailsWith<IllegalArgumentException> { fixture().render(63, 64) }
        assertFailsWith<IllegalArgumentException> { fixture().render(64, 63) }
        assertFailsWith<IllegalArgumentException> { fixture().render(65, 65) }
        assertFailsWith<IllegalArgumentException> { fixture().render(Int.MAX_VALUE, Int.MAX_VALUE) }
    }

    @Test
    fun `region key keeps near-collision RGBA colors distinct`() {
        val first = listOf(46, 94, 142, 255)
        val collision = listOf(47, 63, 142, 255)
        val colors = mapOf(first to 1)
        assertEquals(1, colors[first])
        assertNull(colors[collision])
    }

    @Test
    fun `exact translucent public fixture uses linear premultiplied composition`() {
        val pixels = fixture().render(64, 64)
        assertPixel(pixels, 2, 2, intArrayOf(13, 20, 33, 255))
        assertPixel(pixels, 12, 12, intArrayOf(46, 94, 142, 255))
        assertPixel(pixels, 50, 50, intArrayOf(93, 48, 33, 255))
        assertPixel(pixels, 30, 30, intArrayOf(98, 81, 105, 255))
    }

    @Test
    fun `exact translucent public fixture has literal region counts`() {
        val pixels = fixture().render(64, 64)
        val colors = mapOf(
            listOf(46, 94, 142, 255) to 0,
            listOf(93, 48, 33, 255) to 1,
            listOf(98, 81, 105, 255) to 2,
            listOf(13, 20, 33, 255) to 3,
        )
        val counts = IntArray(4)
        for (offset in pixels.indices step 4) {
            val rgba = (offset until offset + 4).map { pixels[it].toInt() and 0xff }
            counts[colors.getValue(rgba)]++
        }
        assertEquals(intArrayOf(752, 624, 400, 2320).toList(), counts.toList())
    }

    private fun fixture() = SurfaceSrgbSrcOverCpuOracle(
        background = intArrayOf(13, 20, 33, 255),
        rectangles = listOf(
            SurfaceSrgbSrcOverCpuOracle.StraightSrgbRectangle(
                SurfaceSrgbOracleMath.PixelRect(8, 10, 44, 42), intArrayOf(64, 128, 191, 128),
            ),
            SurfaceSrgbSrcOverCpuOracle.StraightSrgbRectangle(
                SurfaceSrgbOracleMath.PixelRect(24, 22, 56, 54), intArrayOf(128, 64, 32, 128),
            ),
        ),
    )

    private fun assertPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        val offset = (y * 64 + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4))
    }

}
