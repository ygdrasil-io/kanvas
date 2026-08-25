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
    fun `asymmetric rrect uses every corner radius independently`() {
        val pixels = SurfaceSrgbRRectCpuOracle(
            BACKGROUND,
            FILL_RRECT,
            SurfaceSrgbRRectCpuOracle.DeviceRRect(
                8f, 8f, 56f, 56f,
                topLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(4f, 8f),
                topRight = SurfaceSrgbRRectCpuOracle.CornerRadii(10f, 4f),
                bottomRight = SurfaceSrgbRRectCpuOracle.CornerRadii(8f, 12f),
                bottomLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(6f, 3f),
            ),
        ).render(64, 64)

        assertPixel(pixels, 10, 8, BACKGROUND)
        assertPixel(pixels, 11, 8, FILL_RRECT)
        assertPixel(pixels, 50, 8, FILL_RRECT)
        assertPixel(pixels, 51, 8, BACKGROUND)
        assertPixel(pixels, 10, 55, BACKGROUND)
        assertPixel(pixels, 11, 55, FILL_RRECT)
        assertPixel(pixels, 49, 55, FILL_RRECT)
        assertPixel(pixels, 50, 55, BACKGROUND)
        assertEquals(2265, count(pixels, FILL_RRECT))
    }

    @Test
    fun `half extent corner radii form one exact ellipse`() {
        val ellipse = SurfaceSrgbRRectCpuOracle.DeviceRRect(12f, 20f, 52f, 44f, 20f, 12f)
        val pixels = SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, ellipse).render(64, 64)

        assertPixel(pixels, 25, 20, BACKGROUND)
        assertPixel(pixels, 26, 20, FILL_RRECT)
        assertPixel(pixels, 37, 20, FILL_RRECT)
        assertPixel(pixels, 38, 20, BACKGROUND)
        assertPixel(pixels, 12, 32, FILL_RRECT)
        assertPixel(pixels, 52, 32, BACKGROUND)
        assertEquals(764, count(pixels, FILL_RRECT))
    }

    @Test
    fun `asymmetric drrect subtracts each independent inner corner`() {
        val outer = asymmetricOuter()
        val inner = SurfaceSrgbRRectCpuOracle.DeviceRRect(
            20f, 20f, 44f, 44f,
            topLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(2f, 4f),
            topRight = SurfaceSrgbRRectCpuOracle.CornerRadii(6f, 2f),
            bottomRight = SurfaceSrgbRRectCpuOracle.CornerRadii(4f, 6f),
            bottomLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(3f, 2f),
        )
        val pixels = SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_DRRECT, outer, inner).render(64, 64)

        assertPixel(pixels, 20, 20, FILL_DRRECT)
        assertPixel(pixels, 21, 20, BACKGROUND)
        assertPixel(pixels, 41, 20, BACKGROUND)
        assertPixel(pixels, 42, 20, FILL_DRRECT)
        assertPixel(pixels, 20, 43, FILL_DRRECT)
        assertPixel(pixels, 21, 43, BACKGROUND)
        assertPixel(pixels, 41, 43, BACKGROUND)
        assertPixel(pixels, 42, 43, FILL_DRRECT)
        assertEquals(1889, count(pixels, FILL_DRRECT))
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
            SurfaceSrgbRRectCpuOracle.DeviceRRect(
                0f, 0f, 10f, 10f,
                topLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(6f, 1f),
                topRight = SurfaceSrgbRRectCpuOracle.CornerRadii(5f, 1f),
                bottomRight = SurfaceSrgbRRectCpuOracle.CornerRadii(1f, 1f),
                bottomLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(1f, 1f),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceSrgbRRectCpuOracle(
                BACKGROUND, FILL_RRECT, validOuter(),
                SurfaceSrgbRRectCpuOracle.DeviceRRect(7f, 20f, 44f, 40f, 2f, 2f),
            )
        }
    }

    private fun scaledRRect() = SurfaceSrgbRRectCpuOracle(BACKGROUND, FILL_RRECT, validOuter())

    private fun asymmetricOuter() = SurfaceSrgbRRectCpuOracle.DeviceRRect(
        6f, 8f, 58f, 56f,
        topLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(4f, 8f),
        topRight = SurfaceSrgbRRectCpuOracle.CornerRadii(10f, 4f),
        bottomRight = SurfaceSrgbRRectCpuOracle.CornerRadii(8f, 12f),
        bottomLeft = SurfaceSrgbRRectCpuOracle.CornerRadii(6f, 3f),
    )

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
