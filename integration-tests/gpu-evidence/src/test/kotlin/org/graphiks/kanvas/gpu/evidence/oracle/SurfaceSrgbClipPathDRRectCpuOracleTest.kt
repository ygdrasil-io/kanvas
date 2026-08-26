package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertEquals

class SurfaceSrgbClipPathDRRectCpuOracleTest {
    @Test
    fun `oracle keeps the outer interior and clip while punching the inner hole`() {
        val bytes = SurfaceSrgbClipPathDRRectCpuOracle(
            listOf(
                SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 8f),
                SurfaceSrgbClipPathDRRectCpuOracle.Point(56f, 8f),
                SurfaceSrgbClipPathDRRectCpuOracle.Point(8f, 55f),
            ),
            SurfaceSrgbClipPathDRRectCpuOracle.RRect(8f, 8f, 52f, 48f, 8f, 8f),
            SurfaceSrgbClipPathDRRectCpuOracle.RRect(20f, 18f, 42f, 39f, 3f, 3f),
            intArrayOf(31, 115, 209, 255),
        ).render(64, 64)
        fun pixel(x: Int, y: Int) = (0 until 4).map { bytes[(y * 64 + x) * 4 + it].toInt() and 0xff }
        assertEquals(listOf(31, 115, 209, 255), pixel(16, 20))
        assertEquals(listOf(0, 0, 0, 0), pixel(28, 28))
        assertEquals(listOf(0, 0, 0, 0), pixel(50, 14))
        assertEquals(753, bytes.asList().chunked(4).count { it[3].toInt() and 0xff != 0 })
    }
}
