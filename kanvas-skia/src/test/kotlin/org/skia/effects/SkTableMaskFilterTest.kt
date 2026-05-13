package org.skia.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises [SkTableMaskFilter] public surface : table-building helpers,
 * `Create` factory, and the mask-remap behaviour.
 */
class SkTableMaskFilterTest {

    @Test
    fun `MakeGammaTable produces monotonic ramp with identity at gamma 1`() {
        val table = ByteArray(256)
        SkTableMaskFilter.MakeGammaTable(table, 1f)
        for (i in 0..255) {
            assertEquals(i, table[i].toInt() and 0xFF, "gamma=1 → identity at $i")
        }
    }

    @Test
    fun `MakeGammaTable squares roughly at gamma 2`() {
        val table = ByteArray(256)
        SkTableMaskFilter.MakeGammaTable(table, 2f)
        // 0 stays 0 ; 255 stays 255.
        assertEquals(0, table[0].toInt() and 0xFF)
        assertEquals(255, table[255].toInt() and 0xFF)
        // Mid-range 128 ≈ (128/255)^2 * 255 = 64.25.
        val mid = table[128].toInt() and 0xFF
        assertTrue(mid in 63..66, "expected ~64 at γ=2, got $mid")
    }

    @Test
    fun `MakeClipTable maps below-min to 0 and above-max to 255`() {
        val table = ByteArray(256)
        SkTableMaskFilter.MakeClipTable(table, 64, 192)
        assertEquals(0, table[0].toInt() and 0xFF)
        assertEquals(0, table[64].toInt() and 0xFF)
        assertEquals(255, table[192].toInt() and 0xFF)
        assertEquals(255, table[255].toInt() and 0xFF)
        // Mid in [64, 192] is half-way.
        val mid = table[128].toInt() and 0xFF
        assertTrue(mid in 125..130, "expected ~127 at midpoint, got $mid")
    }

    @Test
    fun `Create produces a filter that remaps each input alpha`() {
        // Build an "invert" table : table[i] = 255 - i.
        val table = ByteArray(256) { (255 - it).toByte() }
        val mf = SkTableMaskFilter.Create(table)
        assertNotNull(mf)
        assertEquals(0, mf.margin())

        // Run a 4-pixel mask through it.
        val src = byteArrayOf(0, 64, 128.toByte(), 255.toByte())
        val out = mf.filterMask(src, 4, 1)
        assertEquals(255, out[0].toInt() and 0xFF)
        assertEquals(191, out[1].toInt() and 0xFF)
        assertEquals(127, out[2].toInt() and 0xFF)
        assertEquals(0,   out[3].toInt() and 0xFF)
    }

    @Test
    fun `Create defensively copies the table`() {
        val table = ByteArray(256) { it.toByte() }
        val mf = SkTableMaskFilter.Create(table)
        // Mutate caller's table — filter must use the snapshot.
        for (i in 0..255) table[i] = 0
        val out = mf.filterMask(byteArrayOf(100), 1, 1)
        assertEquals(100, out[0].toInt() and 0xFF)
    }

    @Test
    fun `CreateGamma + CreateClip return non-null filters`() {
        assertNotNull(SkTableMaskFilter.CreateGamma(0.5f))
        assertNotNull(SkTableMaskFilter.CreateClip(32, 224))
    }

    @Test
    fun `wrong table size is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkTableMaskFilter.Create(ByteArray(128))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkTableMaskFilter.MakeGammaTable(ByteArray(255), 1f)
        }
    }
}
