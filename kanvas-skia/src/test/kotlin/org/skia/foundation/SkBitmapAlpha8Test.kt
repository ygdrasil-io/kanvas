package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Phase G4a — round-trip exercises for `SkColorType.kAlpha_8` storage
 * on [SkBitmap]. Mirrors the contract documented in `MIGRATION_PLAN_GM_PORT.md`:
 *
 *  - `eraseColor(c)` writes `SkColorGetA(c)` to every byte (RGB ignored).
 *  - `getPixel(x, y)` returns `SkColorSetARGB(byte, 0, 0, 0)`.
 *  - `setPixel(x, y, c)` writes `SkColorGetA(c)` only.
 *  - `asImage()` produces an [SkImage] whose [SkImage.colorType] reports
 *    `kAlpha_8` and that exposes the alpha byte as `(R=0, G=0, B=0, A=byte)`
 *    via [SkImage.peekPixel] (the form [SkBitmapShader] reads).
 */
class SkBitmapAlpha8Test {

    @Test
    fun `eraseColor on Alpha8 writes the alpha byte to every pixel`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeA8(4, 4))
        assertEquals(SkColorType.kAlpha_8, bm.colorType)
        assertEquals(16, bm.pixelsA8.size, "A8 backing buffer must be width*height bytes")

        bm.eraseColor(0xCC112233.toInt())

        for (i in bm.pixelsA8.indices) {
            assertEquals(0xCC.toByte(), bm.pixelsA8[i], "byte $i after eraseColor")
        }
        for (y in 0 until bm.height) {
            for (x in 0 until bm.width) {
                // RGB is dropped — only the alpha byte survives.
                assertEquals(0xCC000000.toInt(), bm.getPixel(x, y),
                    "getPixel($x, $y) should expose alpha-only ARGB")
            }
        }
    }

    @Test
    fun `setPixel on Alpha8 writes alpha and drops RGB`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeA8(4, 4))
        bm.eraseColor(0)

        bm.setPixel(1, 1, 0x80FF0000.toInt())

        // The (1, 1) pixel now carries alpha = 0x80, RGB forced to 0.
        assertEquals(0x80000000.toInt(), bm.getPixel(1, 1))
        // Neighbours stay at the 0 we erased to.
        assertEquals(0, bm.getPixel(0, 0))
        assertEquals(0, bm.getPixel(2, 1))
        assertEquals(0, bm.getPixel(1, 2))
    }

    @Test
    fun `asImage round-trip preserves alpha-only content`() {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeA8(4, 4))
        bm.eraseColor(0)
        bm.setPixel(0, 0, 0xFF112233.toInt())
        bm.setPixel(3, 3, 0x40000000)

        val img = bm.asImage()

        assertEquals(SkColorType.kAlpha_8, img.colorType,
            "asImage must propagate the originating colorType")
        assertEquals(4, img.width)
        assertEquals(4, img.height)

        // (0, 0) — alpha = 0xFF, RGB = 0.
        assertEquals(0xFF000000.toInt(), img.peekPixel(0, 0))
        // (3, 3) — alpha = 0x40, RGB = 0.
        assertEquals(0x40000000, img.peekPixel(3, 3))
        // Background — fully transparent (alpha-only erase to 0).
        assertEquals(0, img.peekPixel(1, 1))

        // Sanity — an 8888 bitmap with the same setPixel would carry the
        // RGB bits through, so this asserts the Alpha8 path is materially
        // different (not just a forwarding alias).
        val bm8888 = SkBitmap(1, 1).also { it.setPixel(0, 0, 0xFF112233.toInt()) }
        assertNotEquals(bm8888.getPixel(0, 0), bm.getPixel(0, 0),
            "8888 and Alpha8 must encode (0, 0) differently for non-grey colours")
    }
}
