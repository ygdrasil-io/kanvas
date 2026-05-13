package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase R1-C — round-trip exercises for [SkColorType.kGray_8] storage on
 * [SkBitmap]. Mirrors the contract for `gm/all_bitmap_configs.cpp` and
 * `gm/bitmapcopy.cpp` consumers:
 *
 *  - `allocPixels(MakeGray8(w, h))` allocates a 1-byte-per-pixel
 *    [SkBitmap.pixelsGray8] backing buffer.
 *  - `eraseColor(c)` collapses RGB to a single Rec.601 luminance byte
 *    and drops alpha (Gray8 is `SkAlphaType.kOpaque`).
 *  - `getPixel(x, y)` returns an opaque ARGB integer with the
 *    luminance byte replicated to R, G, and B.
 */
class SkBitmapGray8Test {

    @Test
    fun `allocPixels for kGray_8 backs by a ByteArray of width times height entries`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeGray8(8, 4))
        assertEquals(SkColorType.kGray_8, bm.colorType)
        assertEquals(32, bm.pixelsGray8.size, "Gray8 backing buffer must be width*height bytes")
    }

    @Test
    fun `eraseColor on Gray8 stores Rec_601 luminance and replicates on read`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeGray8(4, 4))
        bm.eraseColor(0xFFFFFFFF.toInt())   // opaque white
        for (i in bm.pixelsGray8.indices) {
            assertEquals(0xFF.toByte(), bm.pixelsGray8[i],
                "white maps to luminance 255")
        }
        // Read-out replicates luminance to R/G/B and forces alpha = 255.
        for (y in 0 until bm.height) {
            for (x in 0 until bm.width) {
                assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(x, y))
            }
        }
    }

    @Test
    fun `eraseColor on Gray8 with pure red maps to Rec_601 weight`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeGray8(2, 2))
        bm.eraseColor(0xFFFF0000.toInt())   // opaque red
        // R=255 contributes 0.299 * 255 ≈ 76 (Rec.601).
        val lum = bm.pixelsGray8[0].toInt() and 0xFF
        assertTrue(lum in 74..78, "expected red luminance ~76, got $lum")
    }

    @Test
    fun `setPixel on Gray8 collapses RGB to luminance`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeGray8(4, 4))
        bm.eraseColor(0)
        bm.setPixel(2, 2, 0xFF80C040.toInt())
        // Rec.601 luminance of (0x80, 0xC0, 0x40) = (128*77 + 192*150 + 64*29) / 256
        //   = (9856 + 28800 + 1856) / 256 = 40512 / 256 = 158.25 => 158.
        val px = bm.getPixel(2, 2)
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        val b = px and 0xFF
        val a = (px ushr 24) and 0xFF
        assertEquals(0xFF, a, "Gray8 always reads opaque")
        assertEquals(r, g, "Gray8 read-out replicates luminance")
        assertEquals(g, b, "Gray8 read-out replicates luminance")
        assertTrue(r in 155..160, "luminance ~158, got $r")
    }
}
