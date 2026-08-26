package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SurfaceSrgbClipPathRRectCpuOracleTest {
    @Test
    fun `hard triangle clip and asymmetric rrect use independent pixel centers`() {
        val orange = intArrayOf(242, 135, 46, 255)
        val background = intArrayOf(13, 20, 33, 255)
        val pixels = SurfaceSrgbClipPathRRectCpuOracle(
            background,
            listOf(
                SurfaceSrgbClipPathRRectCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathRRectCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathRRectCpuOracle.Point(8f, 55f),
            ),
            SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(
                8f, 8f, 52f, 48f,
                SurfaceSrgbClipPathRRectCpuOracle.Radii(4f, 8f),
                SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 4f),
                SurfaceSrgbClipPathRRectCpuOracle.Radii(8f, 12f),
                SurfaceSrgbClipPathRRectCpuOracle.Radii(6f, 3f),
            ),
            orange,
        ).render(64, 64)

        assertPixel(pixels, 24, 20, orange)
        assertPixel(pixels, 50, 14, background)
        assertPixel(pixels, 8, 8, background)
        assertEquals(1075, count(pixels, orange))
    }

    @Test
    fun `hard triangle clip sees translated asymmetric device rrect directly`() {
        val orange = intArrayOf(242, 135, 46, 255)
        val background = intArrayOf(13, 20, 33, 255)
        val pixels = SurfaceSrgbClipPathRRectCpuOracle(
            background,
            listOf(
                SurfaceSrgbClipPathRRectCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathRRectCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathRRectCpuOracle.Point(8f, 55f),
            ),
            SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(
                12f, 13f, 56f, 53f,
                SurfaceSrgbClipPathRRectCpuOracle.Radii(4f, 8f),
                SurfaceSrgbClipPathRRectCpuOracle.Radii(10f, 4f),
                SurfaceSrgbClipPathRRectCpuOracle.Radii(8f, 12f),
                SurfaceSrgbClipPathRRectCpuOracle.Radii(6f, 3f),
            ),
            orange,
        ).render(64, 64)

        assertPixel(pixels, 24, 20, orange)
        assertPixel(pixels, 10, 12, background)
        assertPixel(pixels, 50, 14, background)
        assertEquals(734, count(pixels, orange))
    }

    @Test
    fun `hard triangle clip distinguishes each finite pure translated device rrect`() {
        val orange = intArrayOf(242, 135, 46, 255)
        val background = intArrayOf(13, 20, 33, 255)
        val triangle = listOf(
            SurfaceSrgbClipPathRRectCpuOracle.Point(8f, 8f),
            SurfaceSrgbClipPathRRectCpuOracle.Point(56f, 8f),
            SurfaceSrgbClipPathRRectCpuOracle.Point(8f, 55f),
        )
        listOf(
            SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(12f, 8f, 56f, 48f, radii(10f, 10f), radii(10f, 10f), radii(10f, 10f), radii(10f, 10f)) to listOf(24, 20, 10, 20, 50, 14),
            SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(8f, 13f, 52f, 53f, radii(4f, 8f), radii(10f, 4f), radii(8f, 12f), radii(6f, 3f)) to listOf(24, 20, 24, 10, 50, 14),
            SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(8f, 25f, 48f, 49f, radii(20f, 12f), radii(20f, 12f), radii(20f, 12f), radii(20f, 12f)) to listOf(11, 32, 49, 12, 45, 30),
            SurfaceSrgbClipPathRRectCpuOracle.DeviceRRect(12f, 3f, 56f, 43f, radii(10f, 10f), radii(10f, 10f), radii(10f, 10f), radii(10f, 10f)) to listOf(24, 20, 18, 44, 50, 14),
        ).forEach { (rrect, samples) ->
            val pixels = SurfaceSrgbClipPathRRectCpuOracle(background, triangle, rrect, orange).render(64, 64)
            assertPixel(pixels, samples[0], samples[1], orange)
            assertPixel(pixels, samples[2], samples[3], background)
            assertPixel(pixels, samples[4], samples[5], background)
        }
    }

    private fun radii(x: Float, y: Float) = SurfaceSrgbClipPathRRectCpuOracle.Radii(x, y)

    private fun count(pixels: ByteArray, color: IntArray) = pixels.asSequence().chunked(4).count {
        it.map(Byte::toInt).map { channel -> channel and 0xff } == color.toList()
    }

    private fun assertPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        val offset = (y * 64 + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4))
    }
}
