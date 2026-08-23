package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceSrgbOracleMathTest {
    @Test
    fun `transfer functions use both sRGB thresholds and round trip`() {
        assertEquals(0.04045 / 12.92, SurfaceSrgbOracleMath.srgbToLinear(0.04045), 1e-12)
        assertEquals(((0.0404501 + 0.055) / 1.055).pow(2.4), SurfaceSrgbOracleMath.srgbToLinear(0.0404501), 1e-12)
        assertEquals(0.0031308 * 12.92, SurfaceSrgbOracleMath.linearToSrgb(0.0031308), 1e-12)
        assertEquals(1.055 * Math.pow(0.0031309, 1.0 / 2.4) - 0.055, SurfaceSrgbOracleMath.linearToSrgb(0.0031309), 1e-12)
        listOf(0.0, 0.018, 0.18, 0.5, 1.0).forEach { value ->
            assertEquals(value, SurfaceSrgbOracleMath.linearToSrgb(SurfaceSrgbOracleMath.srgbToLinear(value)), 1e-12)
        }
    }

    @Test
    fun `q8 clamps and uses half step rounding`() {
        assertEquals(0, SurfaceSrgbOracleMath.q8(0.0))
        assertEquals(255, SurfaceSrgbOracleMath.q8(1.0))
        listOf(0, 127, 254).forEach { n ->
            assertEquals(n + 1, SurfaceSrgbOracleMath.q8((n + 0.5) / 255.0))
            assertEquals(n, SurfaceSrgbOracleMath.q8(Math.nextDown((n + 0.5) / 255.0)))
        }
        assertEquals(0, SurfaceSrgbOracleMath.q8(-1.0))
        assertEquals(255, SurfaceSrgbOracleMath.q8(2.0))
    }

    @Test
    fun `linear premul src over retains fractional state until final store`() {
        val background = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(37, 73, 109, 173))
        val first = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(61, 127, 191, 89))
        val second = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(149, 47, 23, 101))
        val composed = SurfaceSrgbOracleMath.srcOver(
            SurfaceSrgbOracleMath.srcOver(background, first),
            second,
        )
        val prematurelyStored = SurfaceSrgbOracleMath.srcOver(
            SurfaceSrgbOracleMath.decodeStraight(
                SurfaceSrgbOracleMath.storeSrgb(SurfaceSrgbOracleMath.srcOver(background, first)),
            ),
            second,
        )
        assertFalse(
            SurfaceSrgbOracleMath.storeSrgb(composed).contentEquals(
                SurfaceSrgbOracleMath.storeSrgb(prematurelyStored),
            ),
        )
        assertTrue(composed != prematurelyStored)
    }

    @Test
    fun `store sRGB encodes premultiplied RGB without unpremultiplying`() {
        val stored = SurfaceSrgbOracleMath.storeSrgb(
            SurfaceSrgbOracleMath.srcOver(
                SurfaceSrgbOracleMath.decodeStraight(intArrayOf(0, 0, 0, 0)),
                SurfaceSrgbOracleMath.decodeStraight(intArrayOf(255, 0, 0, 128)),
            ),
        )
        assertContentEquals(intArrayOf(188, 0, 0, 128), stored)
    }

    @Test
    fun `premultiplied linear src over stores encoded sRGB bytes`() {
        val result = SurfaceSrgbOracleMath.storeSrgb(
            SurfaceSrgbOracleMath.srcOver(
                SurfaceSrgbOracleMath.decodeStraight(intArrayOf(13, 20, 33, 255)),
                SurfaceSrgbOracleMath.decodeStraight(intArrayOf(64, 127, 191, 128)),
            ),
        )
        assertContentEquals(intArrayOf(46, 93, 142, 255), result)
    }

    @Test
    fun `pixel rect is an oracle-local integer frame`() {
        val rect = SurfaceSrgbOracleMath.PixelRect(7, 7, 57, 57)
        assertEquals(50, rect.width)
        assertEquals(50, rect.height)
        assertTrue(rect.contains(7, 7))
        assertTrue(!rect.contains(57, 57))
    }
}
