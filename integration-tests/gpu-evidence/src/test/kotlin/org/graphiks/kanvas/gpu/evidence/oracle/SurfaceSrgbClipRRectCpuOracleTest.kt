package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SurfaceSrgbClipRRectCpuOracleTest {
    @Test
    fun `solid hard clip uses pixel centers and exact fill count`() {
        val pixels = SurfaceSrgbClipRRectCpuOracle(
            BACKGROUND,
            clip = SurfaceSrgbClipRRectCpuOracle.DeviceRRect(8f, 8f, 56f, 56f, 8f, 8f),
            draws = listOf(SurfaceSrgbClipRRectCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, BLUE)),
        ).render(64, 64)

        assertPixel(pixels, 8, 8, BACKGROUND)
        assertPixel(pixels, 12, 8, BACKGROUND)
        assertPixel(pixels, 13, 8, BLUE)
        assertPixel(pixels, 8, 12, BACKGROUND)
        assertPixel(pixels, 9, 12, BLUE)
        assertPixel(pixels, 32, 32, BLUE)
        assertPixel(pixels, 56, 32, BACKGROUND)
        assertEquals(2256, count(pixels, BLUE))
    }

    @Test
    fun `ellipse hard clip uses anisotropic radii and exact fill count`() {
        val pixels = SurfaceSrgbClipRRectCpuOracle(
            BACKGROUND,
            SurfaceSrgbClipRRectCpuOracle.DeviceRRect(12f, 20f, 52f, 44f, 20f, 12f),
            listOf(SurfaceSrgbClipRRectCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, ORANGE)),
        ).render(64, 64)

        assertPixel(pixels, 25, 20, BACKGROUND)
        assertPixel(pixels, 26, 20, ORANGE)
        assertPixel(pixels, 37, 20, ORANGE)
        assertPixel(pixels, 38, 20, BACKGROUND)
        assertPixel(pixels, 12, 32, ORANGE)
        assertPixel(pixels, 52, 32, BACKGROUND)
        assertEquals(764, count(pixels, ORANGE))
    }

    @Test
    fun `two opaque bands preserve source order inside one hard clip`() {
        val pixels = SurfaceSrgbClipRRectCpuOracle(
            BACKGROUND,
            SurfaceSrgbClipRRectCpuOracle.DeviceRRect(8f, 8f, 56f, 56f, 8f, 8f),
            listOf(
                SurfaceSrgbClipRRectCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, BLUE),
                SurfaceSrgbClipRRectCpuOracle.OpaqueRect(32f, 0f, 64f, 64f, ORANGE),
            ),
        ).render(64, 64)

        assertPixel(pixels, 31, 32, BLUE)
        assertPixel(pixels, 32, 32, ORANGE)
        assertEquals(1128, count(pixels, BLUE))
        assertEquals(1128, count(pixels, ORANGE))
    }

    private fun count(pixels: ByteArray, color: IntArray): Int =
        pixels.asSequence().chunked(4).count { it.map(Byte::toInt).map { value -> value and 0xff } == color.toList() }

    private fun assertPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        val offset = (y * 64 + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4))
    }

    private companion object {
        val BACKGROUND = intArrayOf(13, 20, 33, 255)
        val BLUE = intArrayOf(31, 115, 209, 255)
        val ORANGE = intArrayOf(242, 135, 46, 255)
    }
}
