package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase R1-C — round-trip exercises for [SkColorType.kRGB_565] storage
 * on [SkBitmap]. Mirrors the contract for `gm/all_bitmap_configs.cpp`
 * and `gm/bitmapfilters.cpp` consumers:
 *
 *  - `allocPixels(MakeRGB565(w, h))` allocates a 2-byte-per-pixel
 *    [SkBitmap.pixels565] backing buffer.
 *  - `eraseColor(c)` quantises RGB to 5/6/5 bits and drops alpha (565
 *    is `SkAlphaType.kOpaque`).
 *  - `getPixel(x, y)` returns an opaque ARGB integer with the 5/6/5
 *    channels widened back to 8 bits.
 */
class SkBitmap565Test {

    @Test
    fun `allocPixels for kRGB_565 backs by a ShortArray of width times height entries`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeRGB565(8, 4))
        assertEquals(SkColorType.kRGB_565, bm.colorType)
        assertEquals(32, bm.pixels565.size, "565 backing buffer must be width*height shorts")
    }

    @Test
    fun `eraseColor on 565 packs RGB channels and forces alpha opaque`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeRGB565(4, 4))
        bm.eraseColor(0xFFFF0000.toInt())   // opaque red
        // Per-pixel: 5-bit R=31, 6-bit G=0, 5-bit B=0 => 0xF800.
        for (i in bm.pixels565.indices) {
            assertEquals(0xF800.toShort(), bm.pixels565[i], "byte $i after eraseColor")
        }
        // getPixel widens 5/6/5 back to 8 bits via SkColor16to32 (5→8 :
        // `(v<<3)|(v>>2)`, 6→8 : `(v<<2)|(v>>4)`) ; for R=31 this round-
        // trips to 255 exactly (full-saturation primary). Alpha = 255.
        for (y in 0 until bm.height) {
            for (x in 0 until bm.width) {
                assertEquals(0xFFFF0000.toInt(), bm.getPixel(x, y))
            }
        }
    }

    @Test
    fun `setPixel on 565 round-trips through the 5-6-5 quantisation`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeRGB565(4, 4))
        bm.eraseColor(0)
        bm.setPixel(1, 1, 0xFF112233.toInt())
        val got = bm.getPixel(1, 1)
        // Alpha must be opaque (565 has no alpha channel).
        assertEquals(0xFF, (got ushr 24) and 0xFF)
        // The R/G/B channels are quantised — they should be close to but
        // not exactly the original 8-bit values.
        // 0x11 → 5 bits → 0x10 (round-to-nearest of 17/8 = 2 => widen to 16).
        // 0x22 → 6 bits → 0x20.
        // 0x33 → 5 bits → 0x31.
        assertNotEquals(0xFF112233.toInt(), got, "565 quantisation must lose some precision")
        // Other pixels stay 0 (from eraseColor).
        assertEquals(0xFF000000.toInt(), bm.getPixel(0, 0))
    }
}
