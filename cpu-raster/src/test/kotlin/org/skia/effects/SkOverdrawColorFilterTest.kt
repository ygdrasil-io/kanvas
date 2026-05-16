package org.skia.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.math.SkColorSetARGB
import org.skia.math.SK_ColorTRANSPARENT

/**
 * Verifies [SkOverdrawColorFilter] enforces its alpha-indexed lookup
 * table exactly as specified by the Phase G9b plan :
 *
 *  - α `== 0`            → [SK_ColorTRANSPARENT]
 *  - α `== i + 1` for `i ∈ [0, 5]` → `colors[i]`
 *  - α `>= 6`            → `colors[5]` (clamp)
 */
class SkOverdrawColorFilterTest {

    private val palette: IntArray = intArrayOf(
        0xFFAA0000.toInt(),
        0xFF00AA00.toInt(),
        0xFF0000AA.toInt(),
        0xFFFF0000.toInt(),
        0xFFFF8800.toInt(),
        0xFF000000.toInt(),
    )

    @Test
    fun `MakeWithSkColors maps alpha to colors per overdraw count`() {
        val cf = SkOverdrawColorFilter.MakeWithSkColors(palette)

        // Build a 4-px input bitmap with alphas {0, 1, 3, 6}. The RGB
        // channels are deliberately non-zero so the test catches any
        // implementation that accidentally preserves source colour.
        val bm = SkBitmap(4, 1)
        bm.setPixel(0, 0, SkColorSetARGB(0, 0x12, 0x34, 0x56))
        bm.setPixel(1, 0, SkColorSetARGB(1, 0x12, 0x34, 0x56))
        bm.setPixel(2, 0, SkColorSetARGB(3, 0x12, 0x34, 0x56))
        bm.setPixel(3, 0, SkColorSetARGB(6, 0x12, 0x34, 0x56))

        val out = IntArray(4) { x -> cf.filterColor(bm.getPixel(x, 0)) }

        assertEquals(SK_ColorTRANSPARENT, out[0], "alpha 0 → transparent")
        assertEquals(0xFFAA0000.toInt(), out[1], "alpha 1 → colors[0]")
        assertEquals(0xFF0000AA.toInt(), out[2], "alpha 3 → colors[2]")
        assertEquals(0xFF000000.toInt(), out[3], "alpha 6 → colors[5] (clamp)")
    }

    @Test
    fun `MakeWithSkColors covers every alpha bucket 1 through 6`() {
        val cf = SkOverdrawColorFilter.MakeWithSkColors(palette)
        for (i in 0 until SkOverdrawColorFilter.kNumColors) {
            val input = SkColorSetARGB(i + 1, 0, 0, 0)
            assertEquals(
                palette[i],
                cf.filterColor(input),
                "alpha ${i + 1} should map to colors[$i]",
            )
        }
    }

    @Test
    fun `alpha greater than or equal to 6 clamps to last colour`() {
        val cf = SkOverdrawColorFilter.MakeWithSkColors(palette)
        // Both α=6 and α=255 collapse onto colors[5].
        assertEquals(palette[5], cf.filterColor(SkColorSetARGB(6, 0, 0, 0)))
        assertEquals(palette[5], cf.filterColor(SkColorSetARGB(7, 0, 0, 0)))
        assertEquals(palette[5], cf.filterColor(SkColorSetARGB(255, 0, 0, 0)))
    }

    @Test
    fun `wrong number of colours is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkOverdrawColorFilter.MakeWithSkColors(IntArray(5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkOverdrawColorFilter.MakeWithSkColors(IntArray(7))
        }
    }

    @Test
    fun `defensive copy isolates filter from caller mutation`() {
        val palette = palette.copyOf()
        val cf = SkOverdrawColorFilter.MakeWithSkColors(palette)
        // Mutate the caller's array post-construction — the filter must
        // still report the original entry, not the new one.
        val before = cf.filterColor(SkColorSetARGB(1, 0, 0, 0))
        palette[0] = 0xFFFFFFFF.toInt()
        val after = cf.filterColor(SkColorSetARGB(1, 0, 0, 0))
        assertEquals(before, after, "filter must not observe caller mutations")
        assertNotEquals(0xFFFFFFFF.toInt(), after)
    }

    @Test
    fun `isAlphaUnchanged is false`() {
        val cf = SkOverdrawColorFilter.MakeWithSkColors(palette)
        // The filter rewrites alpha (e.g. α=1 → colors[0].alpha = 0xFF),
        // so the hint must be false to keep the device on the
        // unpremul/repremul slow path.
        assertEquals(false, cf.isAlphaUnchanged())
    }
}
