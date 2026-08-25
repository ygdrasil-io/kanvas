package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SurfaceSrgbRRectCpuOracleTest {
    @Test
    fun `scaled rrect uses pixel centers half open bounds and elliptical corners`() {
        val pixels = scaledRRect().render(64, 64)

        assertPixel(pixels, 15, 16, BACKGROUND)
        assertPixel(pixels, 16, 16, BACKGROUND)
        assertPixel(pixels, 20, 16, FILL_RRECT)
        assertPixel(pixels, 16, 20, FILL_RRECT)
        assertPixel(pixels, 31, 31, FILL_RRECT)
        assertPixel(pixels, 48, 32, BACKGROUND)
        assertEquals(996, count(pixels, FILL_RRECT))
    }

    @Test
    fun `drrect renders the outer rounded shape minus the inner rounded hole`() {
        val pixels = SurfaceSrgbRRectCpuOracle(
            background = BACKGROUND,
            fill = FILL_DRRECT,
            outer = SurfaceSrgbRRectCpuOracle.DeviceRRect(8f, 8f, 56f, 56f, 8f, 8f),
            inner = SurfaceSrgbRRectCpuOracle.DeviceRRect(20f, 20f, 44f, 44f, 4f, 4f),
        ).render(64, 64)

        assertPixel(pixels, 8, 8, BACKGROUND)
        assertPixel(pixels, 13, 8, FILL_DRRECT)
        assertPixel(pixels, 32, 10, FILL_DRRECT)
        assertPixel(pixels, 20, 20, FILL_DRRECT)
        assertPixel(pixels, 21, 21, BACKGROUND)
        assertPixel(pixels, 30, 30, BACKGROUND)
        assertPixel(pixels, 56, 32, BACKGROUND)
        assertEquals(1692, count(pixels, FILL_DRRECT))
    }

    @Test
    fun `oracle validates colors dimensions geometry and nested hole`() {
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(intArrayOf(0, 0, 0), FILL_RRECT, validOuter())
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(intArrayOf(0, 0, 0, 256), FILL_RRECT, validOuter())
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, validOuter()).render(63, 64)
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, SurfaceSrgbRRectCpuOracle.DeviceRRect(Float.NaN, 0f, 4f, 4f, 1f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, SurfaceSrgbRRectCpuOracle.DeviceRRect(4f, 0f, 4f, 4f, 0f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, SurfaceSrgbRRectCpuOracle.DeviceRRect(0f, 4f, 4f, 4f, 1f, 0f))
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, SurfaceSrgbRRectCpuOracle.DeviceRRect(0f, 0f, 4f, 4f, -1f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, SurfaceSrgbRRectCpuOracle.DeviceRRect(0f, 0f, 4f, 4f, 3f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(
                BACKGROUND, FILL_RRECT, validOuter(),
                SurfaceSrgbRRectCpuOracle.DeviceRRect(7f, 20f, 44f, 40f, 2f, 2f),
            )
        }
    }

    private fun scaledRRect() = SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, validOuter())

    private fun validOuter() = SurfaceSrgbRRectCpuOracle.DeviceRRect(16f, 16f, 48f, 48f, 8f, 4f)

    private fun count(pixels: ByteArray, color: IntArray): Int =
        pixels.asSequence().chunked(4).count { it.map(Byte::toInt).map { value -> value and 0xff } == color.toList() }

    private fun assertPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        val offset = (y * 64 + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4))
    }

    private companion object {
        val BACKGROUND = intArrayOf(13, 20, 33, 255)
        val FILL_RRECT = intArrayOf(242, 135, 46, 255)
        val FILL_DRRECT = intArrayOf(31, 115, 209, 255)
    }
}
