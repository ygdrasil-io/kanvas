package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Phase G4b — round-trip exercises for `SkColorType.kBGRA_8888` storage
 * on [SkBitmap]. Mirrors the contract documented in
 * `MIGRATION_PLAN_GM_PORT.md`:
 *
 *  - `eraseColor(c)` fills every pixel with the supplied [SkColor]
 *    interpreted as Pascal-Argb (just like `kRGBA_8888`).
 *  - `getPixel(x, y)` returns a Pascal-Argb `SkColor` value-identical
 *    to what an `kRGBA_8888` bitmap would return for the same writes.
 *  - `setPixel(x, y, c)` writes the same Pascal-Argb value into the
 *    BGRA backing store.
 *  - `asImage()` propagates `kBGRA_8888` as the snapshot's [SkImage.colorType]
 *    but exposes Pascal-Argb pixels (the canonical in-memory form every
 *    downstream consumer reads).
 *
 * The BGRA colour type only affects external byte ordering (PNG / wire
 * encoding); internally we share the Pascal-Argb representation with
 * `kRGBA_8888`. The tests below assert that round-trip equivalence.
 */
class SkBitmapBgra8888Test {

    @Test
    fun `eraseColor on BGRA8888 fills every pixel with the Pascal-Argb value`() {
        val bm = SkBitmap.allocPixels(
            SkImageInfo.Make(4, 4, SkColorType.kBGRA_8888)
        )
        assertEquals(SkColorType.kBGRA_8888, bm.colorType)
        assertEquals(16, bm.pixelsBGRA8888.size, "BGRA backing buffer must be width*height Ints")
        assertEquals(0, bm.pixels8888.size, "RGBA backing buffer must be empty on a BGRA bitmap")

        bm.eraseColor(0xFF112233.toInt())

        for (y in 0 until bm.height) {
            for (x in 0 until bm.width) {
                assertEquals(
                    0xFF112233.toInt(), bm.getPixel(x, y),
                    "getPixel($x, $y) should return the Pascal-Argb fill value"
                )
            }
        }
    }

    @Test
    fun `setPixel on BGRA8888 round-trips through getPixel`() {
        val bm = SkBitmap.allocPixels(
            SkImageInfo.Make(4, 4, SkColorType.kBGRA_8888)
        )
        bm.eraseColor(0)

        bm.setPixel(1, 1, 0xFF445566.toInt())

        assertEquals(0xFF445566.toInt(), bm.getPixel(1, 1))
        // Neighbours stay at the 0 we erased to.
        assertEquals(0, bm.getPixel(0, 0))
        assertEquals(0, bm.getPixel(2, 1))
        assertEquals(0, bm.getPixel(1, 2))
    }

    @Test
    fun `BGRA and RGBA produce value-identical Pascal-Argb pixels`() {
        // Twin bitmaps : same width/height, same writes, only the colourType
        // differs. Every getPixel call must return exactly the same Int,
        // because Pascal-Argb is the canonical internal representation for
        // both colour types.
        val bgra = SkBitmap.allocPixels(
            SkImageInfo.Make(4, 4, SkColorType.kBGRA_8888)
        )
        val rgba = SkBitmap.allocPixels(
            SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888)
        )

        bgra.eraseColor(0xFF112233.toInt())
        rgba.eraseColor(0xFF112233.toInt())

        bgra.setPixel(1, 1, 0xFF445566.toInt())
        rgba.setPixel(1, 1, 0xFF445566.toInt())

        bgra.setPixel(3, 3, 0x80AABBCC.toInt())
        rgba.setPixel(3, 3, 0x80AABBCC.toInt())

        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(
                    rgba.getPixel(x, y), bgra.getPixel(x, y),
                    "BGRA and RGBA must read back the same Pascal-Argb at ($x, $y)"
                )
            }
        }
    }

    @Test
    fun `asImage propagates BGRA colorType and exposes Pascal-Argb pixels`() {
        val bm = SkBitmap.allocPixels(
            SkImageInfo.Make(4, 4, SkColorType.kBGRA_8888)
        )
        bm.eraseColor(0xFF112233.toInt())
        bm.setPixel(0, 0, 0xFFAABBCC.toInt())
        bm.setPixel(3, 3, 0x80445566.toInt())

        val img = bm.asImage()

        assertEquals(
            SkColorType.kBGRA_8888, img.colorType,
            "asImage must propagate the originating colorType"
        )
        assertEquals(4, img.width)
        assertEquals(4, img.height)

        assertEquals(0xFFAABBCC.toInt(), img.peekPixel(0, 0))
        assertEquals(0x80445566.toInt(), img.peekPixel(3, 3))
        assertEquals(0xFF112233.toInt(), img.peekPixel(1, 1))

        // Sanity — a twin 8888 bitmap with the same writes produces an
        // image whose pixels are bit-identical to the BGRA snapshot. This
        // is the value-identity claim of the Phase G4b spec.
        val twin = SkBitmap.allocPixels(SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888))
        twin.eraseColor(0xFF112233.toInt())
        twin.setPixel(0, 0, 0xFFAABBCC.toInt())
        twin.setPixel(3, 3, 0x80445566.toInt())
        val twinImg = twin.asImage()

        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(
                    twinImg.peekPixel(x, y), img.peekPixel(x, y),
                    "BGRA and RGBA snapshots must be value-identical at ($x, $y)"
                )
            }
        }
    }
}
