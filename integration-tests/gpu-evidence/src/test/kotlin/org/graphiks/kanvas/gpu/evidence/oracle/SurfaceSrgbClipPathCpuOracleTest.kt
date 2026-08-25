package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SurfaceSrgbClipPathCpuOracleTest {
    @Test
    fun `triangle clip paints exact orange area and keeps boundary outside`() {
        val pixels = triangleOracle(
            listOf(SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, ORANGE)),
        ).render(64, 64)

        assertPixel(pixels, 8, 8, ORANGE)
        assertPixel(pixels, 55, 8, BACKGROUND)
        assertPixel(pixels, 31, 31, ORANGE)
        assertPixel(pixels, 32, 31, BACKGROUND)
        assertEquals(1128, count(pixels, ORANGE))
    }

    @Test
    fun `concave clip preserves notch with literal winding membership`() {
        val pixels = concaveOracle().render(64, 64)

        assertPixel(pixels, 10, 10, BLUE)
        assertPixel(pixels, 40, 30, BACKGROUND)
        assertPixel(pixels, 40, 44, BLUE)
        assertEquals(1920, count(pixels, BLUE))
    }

    @Test
    fun `ordered two bands reuse one clip and split exact counts at x32`() {
        val pixels = triangleOracle(
            listOf(
                SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, BLUE),
                SurfaceSrgbClipPathCpuOracle.OpaqueRect(32f, 0f, 64f, 64f, ORANGE),
            ),
        ).render(64, 64)

        assertPixel(pixels, 31, 31, BLUE)
        assertPixel(pixels, 32, 8, ORANGE)
        assertPixel(pixels, 32, 31, BACKGROUND)
        assertEquals(852, count(pixels, BLUE))
        assertEquals(276, count(pixels, ORANGE))
    }

    @Test
    fun `point on a literal clip edge is covered while outside remains background`() {
        val oracle = SurfaceSrgbClipPathCpuOracle(
            background = BACKGROUND,
            contours = listOf(
                SurfaceSrgbClipPathCpuOracle.Contour(
                    listOf(
                        SurfaceSrgbClipPathCpuOracle.Point(0f, 0f),
                        SurfaceSrgbClipPathCpuOracle.Point(4f, 0f),
                        SurfaceSrgbClipPathCpuOracle.Point(0f, 4f),
                    ),
                ),
            ),
            draws = listOf(SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 4f, 4f, ORANGE)),
        )
        val pixels = oracle.render(64, 64)

        assertPixel(pixels, 0, 0, ORANGE)
        assertPixel(pixels, 3, 3, BACKGROUND)
    }

    private fun triangleOracle(draws: List<SurfaceSrgbClipPathCpuOracle.OpaqueRect>) =
        SurfaceSrgbClipPathCpuOracle(
            background = BACKGROUND,
            contours = listOf(
                SurfaceSrgbClipPathCpuOracle.Contour(
                    listOf(
                        SurfaceSrgbClipPathCpuOracle.Point(8f, 8f),
                        SurfaceSrgbClipPathCpuOracle.Point(56f, 8f),
                        SurfaceSrgbClipPathCpuOracle.Point(8f, 55f),
                    ),
                ),
            ),
            draws = draws,
        )

    private fun concaveOracle() = SurfaceSrgbClipPathCpuOracle(
        background = BACKGROUND,
        contours = listOf(
            SurfaceSrgbClipPathCpuOracle.Contour(
                listOf(
                    SurfaceSrgbClipPathCpuOracle.Point(8f, 8f),
                    SurfaceSrgbClipPathCpuOracle.Point(56f, 8f),
                    SurfaceSrgbClipPathCpuOracle.Point(56f, 24f),
                    SurfaceSrgbClipPathCpuOracle.Point(32f, 24f),
                    SurfaceSrgbClipPathCpuOracle.Point(32f, 40f),
                    SurfaceSrgbClipPathCpuOracle.Point(56f, 40f),
                    SurfaceSrgbClipPathCpuOracle.Point(56f, 56f),
                    SurfaceSrgbClipPathCpuOracle.Point(8f, 56f),
                ),
            ),
        ),
        draws = listOf(SurfaceSrgbClipPathCpuOracle.OpaqueRect(0f, 0f, 64f, 64f, BLUE)),
    )

    private fun assertPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        val offset = (y * 64 + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4))
    }

    private fun count(pixels: ByteArray, color: IntArray): Int =
        pixels.asList().chunked(4).count { pixel -> pixel.map { it.toInt() and 0xff } == color.toList() }

    private companion object {
        val BACKGROUND = intArrayOf(13, 20, 33, 255)
        val ORANGE = intArrayOf(242, 135, 46, 255)
        val BLUE = intArrayOf(31, 115, 209, 255)
    }
}
