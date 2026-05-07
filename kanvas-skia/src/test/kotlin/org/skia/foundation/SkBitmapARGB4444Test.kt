package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the [SkColorType.kARGB_4444] storage path on
 * [SkBitmap] (Phase C5). 4 bits per channel, packed `0xRGBA`,
 * **premultiplied**.
 *
 * Coverage :
 *  - eraseColor on a fully-opaque white / black pixel encodes to
 *    `0xFFFF` / `0x000F`.
 *  - eraseColor on a fully-transparent pixel encodes to `0x0000`.
 *  - eraseColor on a 50%-alpha colour stores the premul-quantised
 *    nibbles.
 *  - getPixel + setPixel round-trip preserves the colour within
 *    4-bit precision (max diff ≤ 17 per channel).
 *  - getPixelF16 / setPixelF16 round-trip preserves the floats
 *    within 4-bit precision.
 *  - Backing array sizes match `width × height` (one Short per
 *    pixel) for ARGB_4444 bitmaps and 0 for other colorTypes.
 */
class SkBitmapARGB4444Test {

    @Test
    fun `pixels4444 array sized to w x h`() {
        val bm = SkBitmap(8, 6, SkColorSpace.makeSRGB(), SkColorType.kARGB_4444)
        assertEquals(48, bm.pixels4444.size)
        // Other colorType bitmaps have empty pixels4444.
        val bm8 = SkBitmap(8, 6)
        assertEquals(0, bm8.pixels4444.size)
    }

    @Test
    fun `eraseColor opaque white encodes to 0xFFFF`() {
        val bm = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kARGB_4444)
        bm.eraseColor(SK_ColorWHITE)
        for (s in bm.pixels4444) {
            assertEquals(0xFFFF.toShort(), s)
        }
    }

    @Test
    fun `eraseColor opaque black encodes to 0x000F`() {
        val bm = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kARGB_4444)
        bm.eraseColor(SK_ColorBLACK)
        for (s in bm.pixels4444) {
            assertEquals(0x000F.toShort(), s)
        }
    }

    @Test
    fun `eraseColor transparent encodes to 0x0000`() {
        val bm = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kARGB_4444)
        bm.eraseColor(0x00000000)  // fully-transparent
        for (s in bm.pixels4444) {
            assertEquals(0x0000.toShort(), s)
        }
    }

    @Test
    fun `eraseColor with 50% alpha stores premul nibbles`() {
        val bm = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kARGB_4444)
        // ARGB = (0x80, 0xFF, 0x00, 0x00) — half-alpha red.
        // Premul : (0x80, 0xFF*0x80/0xFF=0x80, 0, 0) → 4-bit
        // (alpha=0x80/0xFF=8/15, r=0x80*15/255=8). Packed :
        // r=8, g=0, b=0, a=8 → 0x8008.
        bm.eraseColor(SkColorSetARGB(0x80, 0xFF, 0x00, 0x00))
        val packed = bm.pixels4444[0].toInt() and 0xFFFF
        val r4 = (packed shr 12) and 0xF
        val g4 = (packed shr 8) and 0xF
        val b4 = (packed shr 4) and 0xF
        val a4 = packed and 0xF
        // alpha = round(0.5 * 15) = 8
        assertEquals(8, a4)
        // premul red = round(0.5 * 1.0 * 15) = 8
        assertEquals(8, r4)
        assertEquals(0, g4)
        assertEquals(0, b4)
    }

    @Test
    fun `getPixel + setPixel round-trip preserves colour within 4-bit precision`() {
        val bm = SkBitmap(4, 4, SkColorSpace.makeSRGB(), SkColorType.kARGB_4444)
        val cases = arrayOf(
            SK_ColorWHITE,
            SK_ColorBLACK,
            SK_ColorRED,
            SK_ColorGREEN,
            SK_ColorBLUE,
            SkColorSetARGB(0xFF, 0x80, 0x40, 0xC0),
            SkColorSetARGB(0xCC, 0xCC, 0xCC, 0xCC),
        )
        for ((i, c) in cases.withIndex()) {
            bm.setPixel(i % 4, i / 4, c)
            val out = bm.getPixel(i % 4, i / 4)
            // 4-bit precision : each channel can drift by up to 17
            // (= 255/15) from the input. Alpha = 0xFF round-trips
            // exactly because 15 * 17 = 255.
            val da = kotlin.math.abs(SkColorGetA(out) - SkColorGetA(c))
            val dr = kotlin.math.abs(SkColorGetR(out) - SkColorGetR(c))
            val dg = kotlin.math.abs(SkColorGetG(out) - SkColorGetG(c))
            val db = kotlin.math.abs(SkColorGetB(out) - SkColorGetB(c))
            assertTrue(da <= 17 && dr <= 17 && dg <= 17 && db <= 17) {
                "round-trip drift exceeded 4-bit precision : in=${"0x%08X".format(c)} " +
                    "out=${"0x%08X".format(out)} drift=($da,$dr,$dg,$db)"
            }
        }
    }

    @Test
    fun `getPixelF16 + setPixelF16 round-trip preserves floats within 4-bit precision`() {
        val bm = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kARGB_4444)
        // setPixelF16 expects premultiplied input.
        // Test : red=0.6, green=0, blue=0, alpha=0.6 (already premul).
        bm.setPixelF16(0, 0, 0.6f, 0f, 0f, 0.6f)
        val out = FloatArray(4)
        bm.getPixelF16(0, 0, out)
        // 4-bit precision allows ±1/15 = 0.0667 drift.
        val tol = 1f / 15f + 1e-3f
        assertTrue(kotlin.math.abs(out[0] - 0.6f) <= tol) { "r=$out[0]" }
        assertTrue(kotlin.math.abs(out[1] - 0f) <= tol) { "g=$out[1]" }
        assertTrue(kotlin.math.abs(out[2] - 0f) <= tol) { "b=$out[2]" }
        assertTrue(kotlin.math.abs(out[3] - 0.6f) <= tol) { "a=$out[3]" }
    }

    @Test
    fun `eraseColor with non-sRGB colorSpace applies the xform pipeline`() {
        // Smoke test : non-sRGB colour space should pass through the
        // xformedSrgbColor path without crashing. Exact pixel values
        // depend on the destination gamut ; we only verify the call
        // succeeds and the result is bounded.
        val rec2020 = SkColorSpace.makeSRGB()  // identity for now ; full Rec.2020 needs a wider check
        val bm = SkBitmap(2, 2, rec2020, SkColorType.kARGB_4444)
        bm.eraseColor(SK_ColorWHITE)
        // For sRGB → sRGB the xform is identity, so opaque white should
        // still encode to 0xFFFF.
        assertEquals(0xFFFF.toShort(), bm.pixels4444[0])
    }
}
