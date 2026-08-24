package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SurfaceSrgbGradientCpuOracleTest {
    @Test
    fun `linear repeat wraps hand derived negative and post-first-cycle samples unlike clamp`() {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 4f, 1f)
        val start = SurfaceSrgbGradientCpuOracle.Point(1.5f, .5f)
        val end = SurfaceSrgbGradientCpuOracle.Point(3.5f, .5f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 0, 0, 0),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255),
        )

        val repeat = SurfaceSrgbGradientCpuOracle.linearRepeat(bounds, start, end, stops).render(4, 1)
        val clamp = SurfaceSrgbGradientCpuOracle.linear(bounds, start, end, stops).render(4, 1)

        assertPixel(repeat, 4, 0, 0, intArrayOf(188, 188, 188, 255)) // t_raw = -0.5 -> 0.5
        assertPixel(repeat, 4, 3, 0, intArrayOf(0, 0, 0, 255)) // t_raw = 1.0 -> 0.0
        assertPixel(clamp, 4, 0, 0, intArrayOf(0, 0, 0, 255))
        assertPixel(clamp, 4, 3, 0, intArrayOf(255, 255, 255, 255))
    }

    @Test
    fun `opaque midpoint interpolates in linear light before sRGB storage`() {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 3f, 1f)
        val midpoint = SurfaceSrgbGradientCpuOracle.linear(
            drawBounds = bounds,
            start = SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f),
            end = SurfaceSrgbGradientCpuOracle.Point(2.5f, 0.5f),
            stops = listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 0, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255),
            ),
        ).render(3, 1)

        assertPixel(midpoint, 3, 1, 0, intArrayOf(188, 188, 188, 255))
    }

    @Test
    fun `opaque red to blue midpoint interpolates premultiplied linear channels`() {
        val pixels = SurfaceSrgbGradientCpuOracle.linear(
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 3f, 1f),
            start = SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f),
            end = SurfaceSrgbGradientCpuOracle.Point(2.5f, 0.5f),
            stops = listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 0, 0, 255),
            ),
        ).render(3, 1)

        assertPixel(pixels, 3, 1, 0, intArrayOf(188, 0, 188, 255))
    }

    @Test
    fun `linear gradient uses pixel center geometry clamp and transparent exterior`() {
        val pixels = SurfaceSrgbGradientCpuOracle.linear(
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(1f, 0f, 4f, 1f),
            start = SurfaceSrgbGradientCpuOracle.Point(1.5f, 0.5f),
            end = SurfaceSrgbGradientCpuOracle.Point(3.5f, 0.5f),
            stops = listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 0, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255),
            ),
        ).render(5, 1)

        assertPixel(pixels, 5, 0, 0, intArrayOf(0, 0, 0, 0))
        assertPixel(pixels, 5, 1, 0, intArrayOf(0, 0, 0, 255))
        assertPixel(pixels, 5, 3, 0, intArrayOf(255, 255, 255, 255))
        assertPixel(pixels, 5, 4, 0, intArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `radial gradient uses pixel center distance and clockwise sweep uses screen coordinates`() {
        val radial = SurfaceSrgbGradientCpuOracle.radial(
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 5f, 5f),
            center = SurfaceSrgbGradientCpuOracle.Point(2.5f, 2.5f),
            radius = 2f,
            stops = listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 0, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255),
            ),
        ).render(5, 5)
        assertPixel(radial, 5, 2, 2, intArrayOf(0, 0, 0, 255))
        assertPixel(radial, 5, 3, 2, intArrayOf(188, 188, 188, 255))

        val sweep = SurfaceSrgbGradientCpuOracle.sweep(
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 5f, 5f),
            center = SurfaceSrgbGradientCpuOracle.Point(2.5f, 2.5f),
            startAngle = 0f,
            endAngle = 180f,
            stops = listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 0, 0, 255),
            ),
        ).render(5, 5)
        assertPixel(sweep, 5, 3, 2, intArrayOf(255, 0, 0, 255))
        assertPixel(sweep, 5, 2, 3, intArrayOf(188, 0, 188, 255))
        assertPixel(sweep, 5, 1, 2, intArrayOf(0, 0, 255, 255))
        assertPixel(sweep, 5, 2, 1, intArrayOf(0, 0, 255, 255))
    }

    @Test
    fun `sweep uses positive wrapped angle from start radians`() {
        val pixels = SurfaceSrgbGradientCpuOracle.sweep(
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 3f, 3f),
            center = SurfaceSrgbGradientCpuOracle.Point(1.5f, 1.5f),
            startAngle = -90f,
            endAngle = 90f,
            stops = listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 0, 0, 255),
            ),
        ).render(3, 3)

        assertPixel(pixels, 3, 1, 0, intArrayOf(255, 0, 0, 255))
        assertPixel(pixels, 3, 2, 1, intArrayOf(188, 0, 188, 255))
        assertPixel(pixels, 3, 1, 2, intArrayOf(0, 0, 255, 255))
    }

    @Test
    fun `shifted full-turn sweeps unwrap angles below their normalized starts`() {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 5f, 5f)
        val center = SurfaceSrgbGradientCpuOracle.Point(2.5f, 2.5f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 0, 0),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 0, 0, 255),
        )

        val fortyFiveToFourOhFive = SurfaceSrgbGradientCpuOracle.sweep(bounds, center, 45f, 405f, stops).render(5, 5)
        // East is below the normalized 45° start and unfolds to 360°; south
        // and north are cardinal interpolations on the same full turn.
        assertPixel(fortyFiveToFourOhFive, 5, 3, 2, intArrayOf(99, 0, 240, 255))
        assertPixel(fortyFiveToFourOhFive, 5, 2, 3, intArrayOf(240, 0, 99, 255))
        assertPixel(fortyFiveToFourOhFive, 5, 2, 1, intArrayOf(165, 0, 207, 255))

        val minusNinetyToTwoSeventy = SurfaceSrgbGradientCpuOracle.sweep(bounds, center, -90f, 270f, stops).render(5, 5)
        // North is the start; east crosses zero to 360°, then south and west
        // are the literal quarter, half, and three-quarter cardinal samples.
        assertPixel(minusNinetyToTwoSeventy, 5, 2, 1, intArrayOf(255, 0, 0, 255))
        assertPixel(minusNinetyToTwoSeventy, 5, 3, 2, intArrayOf(225, 0, 137, 255))
        assertPixel(minusNinetyToTwoSeventy, 5, 2, 3, intArrayOf(188, 0, 188, 255))
        assertPixel(minusNinetyToTwoSeventy, 5, 1, 2, intArrayOf(137, 0, 225, 255))
    }

    @Test
    fun `sweep accepts only spans from zero through one full turn`() {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 1f, 1f)
        val center = SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 0, 0),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 0, 0, 255),
        )

        SurfaceSrgbGradientCpuOracle.sweep(bounds, center, 10f, 10f, stops)
        SurfaceSrgbGradientCpuOracle.sweep(bounds, center, 0f, 360f, stops)
        assertIllegalArgument("sweep span must be in [0, 360] degrees") {
            SurfaceSrgbGradientCpuOracle.sweep(bounds, center, 10f, 9f, stops)
        }
        assertIllegalArgument("sweep span must be in [0, 360] degrees") {
            SurfaceSrgbGradientCpuOracle.sweep(bounds, center, 0f, 360.01f, stops)
        }
    }

    @Test
    fun `radial radius must be nonnegative and zero remains degenerate`() {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 1f, 1f)
        val center = SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 0, 0),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 0, 0, 255),
        )

        assertIllegalArgument("radial radius must be finite and nonnegative") {
            SurfaceSrgbGradientCpuOracle.radial(bounds, center, -1f, stops)
        }
        assertPixel(
            SurfaceSrgbGradientCpuOracle.radial(bounds, center, 0f, stops).render(1, 1),
            1, 0, 0, intArrayOf(255, 0, 0, 255),
        )
    }

    @Test
    fun `linear geometry converts float coordinates before subtraction`() {
        val pixels = SurfaceSrgbGradientCpuOracle.linear(
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 1f, 1f),
            start = SurfaceSrgbGradientCpuOracle.Point(-Float.MAX_VALUE, 0f),
            end = SurfaceSrgbGradientCpuOracle.Point(Float.MAX_VALUE, 0f),
            stops = listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 0, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255),
            ),
        ).render(1, 1)

        assertPixel(pixels, 1, 0, 0, intArrayOf(188, 188, 188, 255))
    }

    @Test
    fun `render rejects invalid and overflowing target sizes before allocation`() {
        val oracle = SurfaceSrgbGradientCpuOracle.linear(
            drawBounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 1f, 1f),
            start = SurfaceSrgbGradientCpuOracle.Point(0f, 0f),
            end = SurfaceSrgbGradientCpuOracle.Point(1f, 0f),
            stops = listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 0, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255),
            ),
        )

        assertIllegalArgument("target dimensions must be positive") { oracle.render(0, 1) }
        assertIllegalArgument("target dimensions must be positive") { oracle.render(-1, 1) }
        assertIllegalArgument("target dimensions must be positive") { oracle.render(1, 0) }
        assertIllegalArgument("target dimensions must be positive") { oracle.render(1, -1) }
        assertIllegalArgument("target dimensions exceed RGBA8 byte capacity") { oracle.render(Int.MAX_VALUE, 2) }
    }

    @Test
    fun `stops and geometry reject unsupported or nonfinite values`() {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 1f, 1f)
        val start = SurfaceSrgbGradientCpuOracle.Point(0f, 0.5f)
        val end = SurfaceSrgbGradientCpuOracle.Point(1f, 0.5f)
        fun oracle(stops: List<SurfaceSrgbGradientCpuOracle.Stop>) =
            SurfaceSrgbGradientCpuOracle.linear(bounds, start, end, stops)

        assertIllegalArgument("oracle requires one through sixteen ordered stops in the unit interval") { oracle(emptyList()) }
        assertIllegalArgument("oracle requires one through sixteen ordered stops in the unit interval") {
            oracle(listOf(SurfaceSrgbGradientCpuOracle.Stop(0.1f, 0, 0, 0), SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255)))
        }
        val threeStops = SurfaceSrgbGradientCpuOracle.linear(
            SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 3f, 1f),
            SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f), SurfaceSrgbGradientCpuOracle.Point(2.5f, 0.5f),
            listOf(
                SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 0, 0),
                SurfaceSrgbGradientCpuOracle.Stop(.5f, 0, 255, 0),
                SurfaceSrgbGradientCpuOracle.Stop(1f, 0, 0, 255),
            ),
        ).render(3, 1)
        assertPixel(threeStops, 3, 1, 0, intArrayOf(0, 255, 0, 255))
        assertPixel(threeStops, 3, 2, 0, intArrayOf(0, 0, 255, 255))
        assertIllegalArgument("gradient stop position must be finite") {
            oracle(listOf(SurfaceSrgbGradientCpuOracle.Stop(Float.NaN, 0, 0, 0), SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255)))
        }
        assertIllegalArgument("oracle requires opaque stops") {
            oracle(listOf(SurfaceSrgbGradientCpuOracle.Stop(0f, 0, 0, 0, 254), SurfaceSrgbGradientCpuOracle.Stop(1f, 255, 255, 255)))
        }
        assertIllegalArgument("gradient stop channels must be unsigned bytes") { SurfaceSrgbGradientCpuOracle.Stop(0f, 256, 0, 0) }
        assertIllegalArgument("gradient points must be finite") { SurfaceSrgbGradientCpuOracle.Point(Float.NaN, 0f) }
        assertIllegalArgument("gradient bounds must be finite") { SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, Float.POSITIVE_INFINITY, 1f) }
    }

    @Test
    fun `degenerate geometry selects the start stop`() {
        val bounds = SurfaceSrgbGradientCpuOracle.Rect(0f, 0f, 1f, 1f)
        val stops = listOf(
            SurfaceSrgbGradientCpuOracle.Stop(0f, 255, 0, 0),
            SurfaceSrgbGradientCpuOracle.Stop(1f, 0, 0, 255),
        )
        listOf(
            SurfaceSrgbGradientCpuOracle.linear(bounds, SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f), SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f), stops),
            SurfaceSrgbGradientCpuOracle.radial(bounds, SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f), 0f, stops),
            SurfaceSrgbGradientCpuOracle.sweep(bounds, SurfaceSrgbGradientCpuOracle.Point(0.5f, 0.5f), 90f, 90f, stops),
        ).forEach { oracle -> assertPixel(oracle.render(1, 1), 1, 0, 0, intArrayOf(255, 0, 0, 255)) }
    }

    private fun assertPixel(pixels: ByteArray, width: Int, x: Int, y: Int, expected: IntArray) {
        val offset = (y * width + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }

    private fun assertIllegalArgument(message: String, block: () -> Unit) {
        val exception = assertFailsWith<IllegalArgumentException>(block = block)
        assertEquals(message, exception.message)
    }
}
