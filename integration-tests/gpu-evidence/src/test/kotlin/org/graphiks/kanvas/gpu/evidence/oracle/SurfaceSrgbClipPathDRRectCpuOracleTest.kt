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

    @Test
    fun `oracle uses hand derived positive translated DRRect bounds`() {
        val bytes = SurfaceSrgbClipPathDRRectCpuOracle(
            listOf(
                SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathDRRectCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 55f),
            ),
            SurfaceSrgbClipPathDRRectCpuOracle.RRect(12f, 13f, 56f, 53f, 10f, 10f),
            SurfaceSrgbClipPathDRRectCpuOracle.RRect(26f, 25f, 44f, 43f, 4f, 4f),
            intArrayOf(242, 135, 46, 255),
        ).render(64, 64)
        fun pixel(x: Int, y: Int) = (0 until 4).map { bytes[(y * 64 + x) * 4 + it].toInt() and 0xff }

        assertEquals(listOf(242, 135, 46, 255), pixel(20, 20), "translated outer fill")
        assertEquals(listOf(0, 0, 0, 0), pixel(32, 30), "translated inner hole")
        assertEquals(listOf(0, 0, 0, 0), pixel(50, 14), "outside Winding clip")
        assertEquals(listOf(242, 135, 46, 255), pixel(12, 20), "translated outer left edge")
    }

    @Test
    fun `oracle independently distinguishes every finite non-zero translated DRRect`() {
        val triangle = listOf(
            SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 8f),
            SurfaceSrgbClipPathDRRectCpuOracle.Point(56f, 8f),
            SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 55f),
        )
        data class Case(val outer: SurfaceSrgbClipPathDRRectCpuOracle.RRect, val inner: SurfaceSrgbClipPathDRRectCpuOracle.RRect, val ring: Pair<Int, Int>, val hole: Pair<Int, Int>, val original: Pair<Int, Int>)
        val cases = listOf(
            Case(SurfaceSrgbClipPathDRRectCpuOracle.RRect(12f, 8f, 56f, 48f, 10f, 10f), SurfaceSrgbClipPathDRRectCpuOracle.RRect(26f, 20f, 44f, 38f, 4f, 4f), 20 to 20, 32 to 30, 10 to 20),
            Case(SurfaceSrgbClipPathDRRectCpuOracle.RRect(8f, 13f, 52f, 53f, 4f, 8f), SurfaceSrgbClipPathDRRectCpuOracle.RRect(20f, 23f, 42f, 44f, 3f, 5f), 16 to 20, 28 to 28, 16 to 10),
            Case(SurfaceSrgbClipPathDRRectCpuOracle.RRect(8f, 25f, 48f, 49f, 20f, 12f), SurfaceSrgbClipPathDRRectCpuOracle.RRect(20f, 31f, 36f, 43f, 8f, 6f), 16 to 30, 28 to 36, 33 to 20),
            Case(SurfaceSrgbClipPathDRRectCpuOracle.RRect(12f, 3f, 56f, 43f, 10f, 10f), SurfaceSrgbClipPathDRRectCpuOracle.RRect(26f, 15f, 44f, 33f, 4f, 4f), 20 to 20, 32 to 25, 20 to 47),
        )
        cases.forEach { case ->
            val bytes = SurfaceSrgbClipPathDRRectCpuOracle(triangle, case.outer, case.inner, intArrayOf(242, 135, 46, 255)).render(64, 64)
            fun pixel(point: Pair<Int, Int>) = (0 until 4).map { bytes[(point.second * 64 + point.first) * 4 + it].toInt() and 0xff }
            assertEquals(listOf(242, 135, 46, 255), pixel(case.ring))
            assertEquals(listOf(0, 0, 0, 0), pixel(case.hole))
            assertEquals(listOf(0, 0, 0, 0), pixel(case.original))
            assertEquals(listOf(0, 0, 0, 0), pixel(50 to 14))
        }
    }
}
