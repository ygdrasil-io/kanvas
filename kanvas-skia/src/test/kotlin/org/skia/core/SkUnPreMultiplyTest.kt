package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPreMultiplyARGB
import kotlin.math.abs

/**
 * Exercises [SkUnPreMultiply] : the scale table is the upstream verbatim
 * data, `PMColorToColor` round-trips a premultiplied colour to within ±1
 * per channel, and the table size is 256 (one entry per alpha).
 */
class SkUnPreMultiplyTest {

    @Test
    fun `scale table is 256 entries`() {
        val table = SkUnPreMultiply.GetScaleTable()
        assertEquals(256, table.size)
    }

    @Test
    fun `alpha 0 has scale 0`() {
        assertEquals(0, SkUnPreMultiply.GetScale(0))
    }

    @Test
    fun `alpha 255 round-trips opaque colour without change`() {
        val pm = SkColorSetARGB(255, 200, 150, 80)
        val out = SkUnPreMultiply.PMColorToColor(pm)
        assertEquals(255, SkColorGetA(out))
        assertEquals(200, SkColorGetR(out))
        assertEquals(150, SkColorGetG(out))
        assertEquals(80,  SkColorGetB(out))
    }

    @Test
    fun `PMColorToColor undoes SkPreMultiplyARGB within +-1`() {
        // Round-trip a few non-opaque colours through premul then unpremul.
        val cases = listOf(
            Triple(128, 200, 150) to 64,
            Triple(255, 0, 0)     to 64,
            Triple(64, 128, 200)  to 200,
            Triple(255, 128, 64)  to 128,
        )
        for ((rgb, a) in cases) {
            val pm = SkPreMultiplyARGB(a, rgb.first, rgb.second, rgb.third)
            val unpm = SkUnPreMultiply.PMColorToColor(pm)
            assertEquals(a, SkColorGetA(unpm))
            assertTrue(abs(SkColorGetR(unpm) - rgb.first) <= 1, "R diff for $rgb @ a=$a")
            assertTrue(abs(SkColorGetG(unpm) - rgb.second) <= 1, "G diff for $rgb @ a=$a")
            assertTrue(abs(SkColorGetB(unpm) - rgb.third) <= 1, "B diff for $rgb @ a=$a")
        }
    }

    @Test
    fun `ApplyScale matches the round-via-float formula`() {
        // For every alpha and a few components, the fixed-point ApplyScale
        // must match the float formula `round(c * 255 / a)` within ±1.
        for (a in 1..255) {
            val s = SkUnPreMultiply.GetScale(a)
            for (c in 0..a) {
                val fp = SkUnPreMultiply.ApplyScale(s, c)
                val ref = (c.toFloat() * 255f / a).let { kotlin.math.round(it).toInt() }.coerceIn(0, 255)
                assertTrue(abs(fp - ref) <= 1, "α=$a c=$c got=$fp expected≈$ref")
            }
        }
    }

    @Test
    fun `UnPreMultiplyPreservingByteOrder is an alias for PMColorToColor`() {
        val pm = SkPreMultiplyARGB(128, 200, 150, 80)
        assertEquals(
            SkUnPreMultiply.PMColorToColor(pm),
            SkUnPreMultiply.UnPreMultiplyPreservingByteOrder(pm),
        )
    }
}
