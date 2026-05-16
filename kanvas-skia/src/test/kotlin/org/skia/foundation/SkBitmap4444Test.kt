package org.skia.foundation



import org.graphiks.math.between
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Phase G4c — focussed round-trip exercises for the `kARGB_4444` storage
 * path on [SkBitmap]. Adds the spec-mandated cases (allocPixels +
 * eraseColor + getPixel for an opaque non-grey colour) on top of the
 * Phase C5 [SkBitmapARGB4444Test] coverage, plus the Phase G4c
 * `SkImageInfo.Make4444` factory and `asImage()` propagation tests that
 * mirror Phase G4a / G4b's checklists.
 *
 * Spec contract reminder :
 *
 *  - `allocPixels(info)` with `info.colorType == kARGB_4444` allocates
 *    a `ShortArray(width * height)`.
 *  - `eraseColor(c)` packs `RRRR_GGGG_BBBB_AAAA` into the short using
 *    round-to-nearest 4-bit quantisation of `c`'s **premultiplied**
 *    float channels. For opaque colours premul is a no-op and the 4-bit
 *    value of each channel is `(c8 + 8) >> 4` (= round of `c8 / 17`).
 *  - `getPixel(x, y)` widens the 4-bit channels via `v * 17` (== shift
 *    left by 4 + replicated low nibble — i.e. `0x0F → 0xFF`,
 *    `0x05 → 0x55`) then unpremuls the colour channels by alpha.
 *    Returns a Pascal-Argb [SkColor].
 *  - `setPixel(x, y, c)` is the inverse.
 *  - `asImage()` propagates [SkColorType.kARGB_4444] as the snapshot's
 *    [SkImage.colorType] while exposing the canonical Pascal-Argb
 *    pixels every downstream consumer reads.
 *
 * The spec uses the colloquial name "kRGBA_4444" (the bit layout, not
 * the Skia enum) ; the test file name matches the spec while the
 * actual enum threaded through the code is the Skia-canonical
 * [SkColorType.kARGB_4444].
 */
class SkBitmap4444Test {

    @Test
    fun `allocPixels with Make4444 allocates a width-times-height ShortArray`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.Make4444(8, 6))
        assertEquals(SkColorType.kARGB_4444, bm.colorType)
        assertEquals(48, bm.pixels4444.size, "4444 backing buffer must be width*height Shorts")
        // The other-colorType backing stores must remain empty on a 4444 bitmap.
        assertEquals(0, bm.pixels8888.size, "RGBA 8888 buffer must be empty on a 4444 bitmap")
        assertEquals(0, bm.pixelsBGRA8888.size, "BGRA 8888 buffer must be empty on a 4444 bitmap")
        assertEquals(0, bm.pixelsA8.size, "Alpha8 buffer must be empty on a 4444 bitmap")
        assertEquals(0, bm.pixelsF16.size, "F16 buffer must be empty on a 4444 bitmap")
    }

    @Test
    fun `SkImageInfo Make4444 reports 2 bytes per pixel`() {
        val info = SkImageInfo.Make4444(4, 4)
        assertEquals(SkColorType.kARGB_4444, info.colorType)
        assertEquals(SkAlphaType.kPremul, info.alphaType)
        assertEquals(2, info.bytesPerPixel(), "kARGB_4444 must report 2 bytes per pixel")
        assertEquals(8, info.minRowBytes(), "minRowBytes = width * 2 for 4444")
    }

    /**
     * Spec test case : `eraseColor(0xFFFF8800)`. Channels are
     * `A=0xFF R=0xFF G=0x88 B=0x00`. Premul of opaque is identity, so
     * each channel quantises to its 4-bit equivalent :
     *
     *   R : `(0xFF + 8) >> 4` = `0x10` clamped to `0xF`. Equivalently
     *       `round(255/17)` = `round(15.0)` = `15`.
     *   G : `round(0x88/17)` = `round(8.0)` = `8`. (0x88 = 8*17 exactly.)
     *   B : 0.
     *   A : 15.
     *
     * Packed short : `(R << 12) | (G << 8) | (B << 4) | A`
     *              = `(15 << 12) | (8 << 8) | (0 << 4) | 15`
     *              = `0xF80F`.
     *
     * `getPixel` widens each 4-bit channel via `v * 17` (which equals
     * `(v << 4) | v` — the bit-replication form the spec describes) :
     *
     *   R : 15 * 17 = 255 = 0xFF
     *   G : 8 * 17  = 136 = 0x88
     *   B : 0
     *   A : 255
     *
     * Unpremul of an opaque pixel is a no-op, so the round-trip is
     * **exact** for `0xFFFF8800` : input and readback match bit-for-bit.
     */
    @Test
    fun `eraseColor 0xFFFF8800 packs to 0xF80F and round-trips exactly`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.Make4444(2, 2))
        bm.eraseColor(0xFFFF8800.toInt())

        // Packed short is 0xF80F across every pixel.
        for (s in bm.pixels4444) {
            assertEquals(0xF80F.toShort(), s, "every pixel should pack to 0xF80F after eraseColor(0xFFFF8800)")
        }

        // getPixel round-trips exactly (alpha == 0xFF, every channel ∈ {n*17}).
        for (y in 0 until bm.height) {
            for (x in 0 until bm.width) {
                assertEquals(
                    0xFFFF8800.toInt(), bm.getPixel(x, y),
                    "getPixel($x, $y) should round-trip 0xFFFF8800 exactly through 4-bit quantisation",
                )
            }
        }
    }

    @Test
    fun `setPixel and getPixel round-trip known 4-bit-friendly colours`() {
        // Every channel = n * 17 → exact round-trip through 4-bit
        // quantisation for opaque pixels (no premul / unpremul drift).
        val exactCases = arrayOf(
            0xFF000000.toInt(),   // opaque black → packed 0x000F
            0xFFFFFFFF.toInt(),   // opaque white → packed 0xFFFF
            0xFF112233.toInt(),   // 0x11 = 1*17, 0x22 = 2*17, 0x33 = 3*17 → exact
            0xFF7711AA.toInt(),   // 0x77, 0x11, 0xAA all in {n*17} → exact
            0xFFFF8800.toInt(),   // spec example → exact (see test above)
        )
        val bm = SkBitmap.allocPixels(SkImageInfo.Make4444(4, 4))
        for ((i, c) in exactCases.withIndex()) {
            val x = i % 4
            val y = i / 4
            bm.setPixel(x, y, c)
            assertEquals(
                c, bm.getPixel(x, y),
                "($x, $y) should round-trip ${"0x%08X".format(c)} exactly",
            )
        }
    }

    @Test
    fun `setPixel and getPixel approximate round-trip preserves 4-bit precision`() {
        // Channels not in {n * 17} drift up to ±8 through round-to-nearest
        // 4-bit quantisation — well within the upstream tolerance of ±17.
        // The point of this test is to make sure the quantised readback
        // stays inside the (input ± 8) window, i.e. that we picked the
        // nearest representable nibble rather than truncating.
        val bm = SkBitmap.allocPixels(SkImageInfo.Make4444(4, 4))
        val cases = arrayOf(
            0xFF808080.toInt(),   // mid-grey — 0x80 → round(128/17) = 8 → 8*17 = 136 = 0x88
            0xFF7F7F7F.toInt(),   // just below mid-grey
            0xFFC04080.toInt(),   // mixed
        )
        for ((i, c) in cases.withIndex()) {
            val x = i % 4
            val y = i / 4
            bm.setPixel(x, y, c)
            val out = bm.getPixel(x, y)
            // Each 4-bit channel widens to v*17, so the readback lands on
            // one of {0, 17, 34, ..., 255} — never more than 8 away from
            // any input byte (the maximum gap between adjacent buckets is 17).
            val da = kotlin.math.abs(SkColorGetA(out) - SkColorGetA(c))
            val dr = kotlin.math.abs(SkColorGetR(out) - SkColorGetR(c))
            val dg = kotlin.math.abs(SkColorGetG(out) - SkColorGetG(c))
            val db = kotlin.math.abs(SkColorGetB(out) - SkColorGetB(c))
            // 4-bit precision : each channel can drift by up to ⌈17/2⌉ = 9
            // from the input. Alpha = 0xFF round-trips exactly (15 * 17 = 255).
            assert(da <= 9 && dr <= 9 && dg <= 9 && db <= 9) {
                "round-trip drift exceeded 4-bit precision : in=${"0x%08X".format(c)} " +
                    "out=${"0x%08X".format(out)} drift=($da,$dr,$dg,$db)"
            }
        }
    }

    @Test
    fun `setPixel writes only the targeted pixel, neighbours untouched`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.Make4444(4, 4))
        bm.eraseColor(0)

        bm.setPixel(2, 2, 0xFFFF8800.toInt())

        // The targeted pixel readback should round-trip exactly.
        assertEquals(0xFFFF8800.toInt(), bm.getPixel(2, 2))
        // Every other pixel stays at the erased-to-zero value.
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                if (x == 2 && y == 2) continue
                assertEquals(0, bm.getPixel(x, y), "($x, $y) should be untouched (still 0)")
            }
        }
    }

    @Test
    fun `asImage propagates kARGB_4444 colorType and exposes the round-tripped pixels`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.Make4444(4, 4))
        bm.eraseColor(0xFFFF8800.toInt())
        bm.setPixel(0, 0, 0xFFFFFFFF.toInt())
        bm.setPixel(3, 3, 0xFF000000.toInt())

        val img = bm.asImage()

        assertEquals(
            SkColorType.kARGB_4444, img.colorType,
            "asImage must propagate the originating colorType",
        )
        assertEquals(4, img.width)
        assertEquals(4, img.height)

        // peekPixel exposes the canonical Pascal-Argb snapshot. Each pixel
        // is the result of the 4-bit pack-then-unpack round-trip — exact
        // for these inputs (every channel ∈ {n * 17}).
        assertEquals(0xFFFFFFFF.toInt(), img.peekPixel(0, 0))
        assertEquals(0xFF000000.toInt(), img.peekPixel(3, 3))
        assertEquals(0xFFFF8800.toInt(), img.peekPixel(1, 1))

        // Sanity — an 8888 twin with the same writes produces a snapshot
        // that, for these exact 4-bit-friendly inputs, is bit-identical.
        // (The point is to assert the 4444 path snapshots faithfully,
        // not that it loses precision — round-tripping at 4-bit
        // precision the 8888 path can also represent.)
        val twin = SkBitmap.allocPixels(SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888))
        twin.eraseColor(0xFFFF8800.toInt())
        twin.setPixel(0, 0, 0xFFFFFFFF.toInt())
        twin.setPixel(3, 3, 0xFF000000.toInt())
        val twinImg = twin.asImage()
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(
                    twinImg.peekPixel(x, y), img.peekPixel(x, y),
                    "4444 and 8888 snapshots must agree on a 4-bit-friendly input at ($x, $y)",
                )
            }
        }
    }

    @Test
    fun `kARGB_4444 must encode mid-grey differently from 8888 (loss is real)`() {
        // Sanity check that the test wiring really runs the 4444 path
        // and isn't aliasing to the 8888 backing. Mid-grey 0x80 is not in
        // {n * 17} ; the 4444 readback rounds to 0x88, the 8888 path
        // preserves 0x80 exactly. So the two snapshots must differ.
        val bm4 = SkBitmap.allocPixels(SkImageInfo.Make4444(1, 1))
        bm4.eraseColor(0xFF808080.toInt())
        val bm8 = SkBitmap.allocPixels(SkImageInfo.Make(1, 1, SkColorType.kRGBA_8888))
        bm8.eraseColor(0xFF808080.toInt())
        assertNotEquals(
            bm8.getPixel(0, 0), bm4.getPixel(0, 0),
            "mid-grey 0x80 must readback differently between 4444 and 8888",
        )
        // And the 4444 readback should specifically be 0xFF888888.
        assertEquals(0xFF888888.toInt(), bm4.getPixel(0, 0))
    }
}
