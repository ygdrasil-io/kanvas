package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals

class SurfaceSrgbTwoStopRadialGradientStrokeCpuOracleTest {
    @Test
    fun `radial stroke oracle keeps alpha opaque and clamps center and exterior samples`() {
        val oracle = SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle(
            listOf(
                SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Rect(0, 0, 4, 1),
                SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Rect(0, 1, 1, 4),
                SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Rect(3, 1, 4, 4),
                SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Rect(1, 3, 3, 4),
            ),
            SurfaceSrgbTwoStopRadialGradientStrokeCpuOracle.Point(.5, .5), 1.0,
            intArrayOf(255, 0, 0, 255), intArrayOf(0, 0, 255, 255),
        )
        val pixels = oracle.render(4, 4)
        fun pixel(x: Int, y: Int) = pixels.copyOfRange((y * 4 + x) * 4, (y * 4 + x + 1) * 4)
        assertContentEquals(byteArrayOf(-1, 0, 0, -1), pixel(0, 0))
        assertContentEquals(byteArrayOf(0, 0, -1, -1), pixel(3, 0))
        assertContentEquals(byteArrayOf(0, 0, 0, 0), pixel(1, 1))
    }
}
