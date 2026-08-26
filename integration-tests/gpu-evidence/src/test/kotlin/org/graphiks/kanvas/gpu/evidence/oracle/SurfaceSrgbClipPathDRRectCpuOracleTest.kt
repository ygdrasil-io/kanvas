package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertEquals

class SurfaceSrgbClipPathDRRectCpuOracleTest {
    @Test
    fun `oracle represents asymmetric corner radii and all DRRect visibility classes`() {
        val bytes = SurfaceSrgbClipPathDRRectCpuOracle(
            listOf(
                SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathDRRectCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 55f),
            ),
            SurfaceSrgbClipPathDRRectCpuOracle.RRect(
                8f, 8f, 52f, 48f,
                SurfaceSrgbClipPathDRRectCpuOracle.Radii(4f, 8f),
                SurfaceSrgbClipPathDRRectCpuOracle.Radii(10f, 4f),
                SurfaceSrgbClipPathDRRectCpuOracle.Radii(8f, 12f),
                SurfaceSrgbClipPathDRRectCpuOracle.Radii(6f, 3f),
            ),
            SurfaceSrgbClipPathDRRectCpuOracle.RRect(
                20f, 18f, 42f, 39f,
                SurfaceSrgbClipPathDRRectCpuOracle.Radii(3f, 5f),
                SurfaceSrgbClipPathDRRectCpuOracle.Radii(6f, 2f),
                SurfaceSrgbClipPathDRRectCpuOracle.Radii(4f, 7f),
                SurfaceSrgbClipPathDRRectCpuOracle.Radii(2f, 3f),
            ),
            intArrayOf(31, 115, 209, 255),
        ).render(64, 64)
        fun pixel(x: Int, y: Int) = (0 until 4).map { bytes[(y * 64 + x) * 4 + it].toInt() and 0xff }
        assertEquals(listOf(31, 115, 209, 255), pixel(16, 20), "outer fill inside clip")
        assertEquals(listOf(0, 0, 0, 0), pixel(28, 28), "inner hole")
        assertEquals(listOf(0, 0, 0, 0), pixel(6, 20), "outside DRRect geometry")
        assertEquals(listOf(0, 0, 0, 0), pixel(50, 14), "outside Winding clip")
        assertEquals(772, bytes.asList().chunked(4).count { it[3].toInt() and 0xff != 0 })
    }
}
